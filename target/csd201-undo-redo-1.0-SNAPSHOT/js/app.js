/**
 * CSD201 Undo/Redo Editor — Client-Side Controller (app.js)
 *
 * Responsibilities:
 *   - Connect UI events to the Java Servlet backend via fetch().
 *   - Detect text changes in the textarea and send them as Operations.
 *   - Intercept Ctrl+Z / Ctrl+Y to use custom undo/redo (not browser-native).
 *   - Render the Action History panel.
 *   - Render benchmark result tables.
 *   - Handle dark-mode toggle (client-side).
 *   - Handle search highlighting (client-side rendering).
 *
 * This file does NOT implement undo/redo logic itself. All logic is
 * handled server-side in Java.
 */

// ═══════════════════════════════════════════════════════════════
// DOM REFERENCES
// ═══════════════════════════════════════════════════════════════

const $ = (id) => document.getElementById(id);

const editorTextarea         = $('editor-textarea');
const editorBackdrop         = $('editor-backdrop');
const editorHighlights       = $('editor-highlights');
const scrollMarkersContainer = $('scroll-markers-container');
const statusMessage          = $('status-message');
const statusMemory           = $('status-memory');
const statusUndoCount        = $('status-undo-count');
const statusRedoCount        = $('status-redo-count');
const historyList            = $('history-list');
const historyCount           = $('history-count');
const benchmarkResults       = $('benchmark-results');
const benchmarkDesc          = $('benchmark-description');
const modelSelect            = $('model-select');
const modalOverlay           = $('modal-overlay');
const modalTitle             = $('modal-title');
const modalBody              = $('modal-body');
const modalConfirm           = $('modal-confirm');
const modalCancel            = $('modal-cancel');
const modalClose             = $('modal-close');
const btnExport              = $('btn-export');

// ═══════════════════════════════════════════════════════════════
// STATE
// ═══════════════════════════════════════════════════════════════

let previousContent = '';          // Track previous textarea content for diff detection
let isUpdatingTextarea = false;    // Guard against recursive input events
let pendingRequest = false;        // True while queued editor changes are syncing
let editorRequestQueue = [];       // Preserve typing order while requests are in flight
let editorQueueIdleResolvers = [];
let lastBenchmarkResults = null;   // For export
let lastBenchmarkRQ = '';
let currentSearchResults = [];     // Track active search match indices
let currentSearchKeyword = '';     // Track active search keyword

const SAVED_DOCUMENT_KEY = 'csd201.undoRedo.savedContent';

// ═══════════════════════════════════════════════════════════════
// API HELPERS
// ═══════════════════════════════════════════════════════════════

async function apiEditor(payload) {
    const res = await fetch('api/editor', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
    });
    return await res.json();
}

async function apiBenchmark(rq) {
    const res = await fetch('api/benchmark', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ rq })
    });
    return await res.json();
}

function getLocalSavedContent() {
    try {
        return localStorage.getItem(SAVED_DOCUMENT_KEY);
    } catch (e) {
        return null;
    }
}

function setLocalSavedContent(content) {
    try {
        localStorage.setItem(SAVED_DOCUMENT_KEY, content);
    } catch (e) {
        console.warn('Could not save document locally:', e);
    }
}

function waitForEditorQueue() {
    if (!pendingRequest && editorRequestQueue.length === 0) {
        return Promise.resolve();
    }
    return new Promise(resolve => editorQueueIdleResolvers.push(resolve));
}

function notifyEditorQueueIdle() {
    const resolvers = editorQueueIdleResolvers;
    editorQueueIdleResolvers = [];
    resolvers.forEach(resolve => resolve());
}

function buildTypingPayload(change) {
    if (change.type === 'insert') {
        return {
            action: 'insert',
            position: change.position,
            text: change.text,
            fromTyping: true
        };
    }
    if (change.type === 'delete') {
        return {
            action: 'delete',
            position: change.position,
            length: change.length,
            fromTyping: true
        };
    }
    if (change.type === 'replace') {
        return {
            action: 'replace',
            position: change.position,
            length: change.length,
            newText: change.newText,
            fromTyping: true
        };
    }
    return null;
}

function enqueueEditorChange(change) {
    const payload = buildTypingPayload(change);
    if (!payload) return;

    editorRequestQueue.push(payload);
    processEditorQueue();
}

async function processEditorQueue() {
    if (pendingRequest) return;

    pendingRequest = true;
    try {
        while (editorRequestQueue.length > 0) {
            const payload = editorRequestQueue.shift();
            const data = await apiEditor(payload);
            const hasMoreLocalWork = editorRequestQueue.length > 0 || editorTextarea.value !== data.content;

            updateUI(data, { preserveTextarea: hasMoreLocalWork });
            if (data && data.success === false) {
                editorRequestQueue = [];
                break;
            }
        }
    } catch (e) {
        console.error('Input sync error:', e);
        showStatus('Failed to sync editor changes.');
    } finally {
        pendingRequest = false;
        notifyEditorQueueIdle();
    }
}

// ═══════════════════════════════════════════════════════════════
// UI UPDATE
// ═══════════════════════════════════════════════════════════════

function updateUI(data, options = {}) {
    if (!data) return;

    // Update textarea content (guard against triggering input event)
    if (data.content !== undefined && !options.preserveTextarea) {
        isUpdatingTextarea = true;
        const selStart = editorTextarea.selectionStart;
        const selEnd   = editorTextarea.selectionEnd;
        editorTextarea.value = data.content;
        previousContent = data.content;
        // Restore cursor position
        editorTextarea.setSelectionRange(
            Math.min(selStart, data.content.length),
            Math.min(selEnd, data.content.length)
        );
        isUpdatingTextarea = false;
    }

    // Status message
    if (data.message) {
        showStatus(data.message);
    }

    // Meta info
    if (data.memoryUsage !== undefined) {
        statusMemory.textContent = formatBytes(data.memoryUsage);
    }
    if (data.undoCount !== undefined) {
        statusUndoCount.textContent = data.undoCount;
    }
    if (data.redoCount !== undefined) {
        statusRedoCount.textContent = data.redoCount;
    }

    // Undo/Redo button states
    $('btn-undo').disabled = !data.canUndo;
    $('btn-redo').disabled = !data.canRedo;

    // History viewer
    if (data.history) {
        renderHistory(data.history);
    }

    // Refresh search highlights on UI updates
    refreshSearchHighlights();
}

function showStatus(msg) {
    statusMessage.textContent = msg;
    statusMessage.classList.remove('flash');
    // Trigger reflow for re-animation
    void statusMessage.offsetWidth;
    statusMessage.classList.add('flash');
}

function formatBytes(bytes) {
    if (bytes === 0) return '0 B';
    const units = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return (bytes / Math.pow(1024, i)).toFixed(i > 0 ? 2 : 0) + ' ' + units[i];
}

// ═══════════════════════════════════════════════════════════════
// HISTORY VIEWER
// ═══════════════════════════════════════════════════════════════

function renderHistory(history) {
    historyCount.textContent = history.length + ' action' + (history.length !== 1 ? 's' : '');

    const undoItems = history.filter(item => item.active);
    const redoItems = history.filter(item => !item.active);

    // Render Undo List
    if (undoItems.length === 0) {
        historyList.innerHTML = '<div class="history-empty">No active actions.</div>';
    } else {
        let html = '';
        for (let i = 0; i < history.length; i++) {
            const item = history[i];
            if (!item.active) continue;
            const isCurrentState = (i === history.length - 1 || !history[i+1].active);
            html += `
                <div class="history-item ${isCurrentState ? 'active' : ''}"
                     data-index="${i}" onclick="handleHistoryClick(${i})">
                    <span class="history-index">${i + 1}</span>
                    <span class="history-type-badge ${item.type}">${item.type}</span>
                    <span class="history-desc">${escapeHtml(item.description)}</span>
                </div>`;
        }
        historyList.innerHTML = html;
        historyList.scrollTop = historyList.scrollHeight;
    }

    // Render Redo List
    const redoList = $('redo-list');
    if (redoItems.length === 0) {
        redoList.innerHTML = '<div class="history-empty">No undone actions.</div>';
    } else {
        let html = '';
        for (let i = 0; i < history.length; i++) {
            const item = history[i];
            if (item.active) continue;
            html += `
                <div class="history-item future"
                     data-index="${i}" onclick="handleHistoryClick(${i})">
                    <span class="history-index">${i + 1}</span>
                    <span class="history-type-badge ${item.type}">${item.type}</span>
                    <span class="history-desc">${escapeHtml(item.description)}</span>
                </div>`;
        }
        redoList.innerHTML = html;
        redoList.scrollTop = 0;
    }
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

async function handleHistoryClick(index) {
    const data = await apiEditor({ action: 'undoTo', targetIndex: index });
    updateUI(data);
}

// ═══════════════════════════════════════════════════════════════
// TEXT CHANGE DETECTION (diff-based)
// ═══════════════════════════════════════════════════════════════

/**
 * Detect the minimal change between oldText and newText.
 * Returns { type, position, oldText, newText } or null if unchanged.
 */
function detectChange(oldText, newText) {
    if (oldText === newText) return null;

    let start = 0;
    while (start < oldText.length && start < newText.length && oldText[start] === newText[start]) {
        start++;
    }

    let oldEnd = oldText.length;
    let newEnd = newText.length;
    while (oldEnd > start && newEnd > start && oldText[oldEnd - 1] === newText[newEnd - 1]) {
        oldEnd--;
        newEnd--;
    }

    const deleted  = oldText.substring(start, oldEnd);
    const inserted = newText.substring(start, newEnd);

    if (deleted.length === 0 && inserted.length > 0) {
        return { type: 'insert', position: start, text: inserted };
    } else if (deleted.length > 0 && inserted.length === 0) {
        return { type: 'delete', position: start, length: deleted.length };
    } else if (deleted.length > 0 && inserted.length > 0) {
        return { type: 'replace', position: start, length: deleted.length, newText: inserted };
    }
    return null;
}

// ═══════════════════════════════════════════════════════════════
// SEARCH & HIGHLIGHTING HELPERS
// ═══════════════════════════════════════════════════════════════

function localSearch(content, keyword) {
    if (!keyword) return [];
    const matches = [];
    const lowerContent = content.toLowerCase();
    const lowerKeyword = keyword.toLowerCase();
    let idx = 0;
    while ((idx = lowerContent.indexOf(lowerKeyword, idx)) !== -1) {
        matches.push({ start: idx, end: idx + keyword.length });
        idx += 1;
    }
    return matches;
}

function getLineIndex(text, charIndex) {
    return text.substring(0, charIndex).split('\n').length - 1;
}

function applySearchHighlighting(searchResults, keyword) {
    currentSearchResults = searchResults || [];
    currentSearchKeyword = keyword || '';

    const content = editorTextarea.value;
    
    // Clear existing
    editorHighlights.innerHTML = '';
    scrollMarkersContainer.innerHTML = '';

    if (!keyword || currentSearchResults.length === 0) {
        // Just mirror the text without formatting
        editorHighlights.textContent = content;
        return;
    }

    // 1. Build Highlighted HTML
    let html = '';
    let lastIndex = 0;
    for (const match of currentSearchResults) {
        const start = match.start;
        const end = match.end;
        
        if (start < lastIndex || end > content.length || start > content.length) {
            continue;
        }

        html += escapeHtml(content.substring(lastIndex, start));
        html += `<span class="search-highlight">${escapeHtml(content.substring(start, end))}</span>`;
        lastIndex = end;
    }
    html += escapeHtml(content.substring(lastIndex));
    
    if (content.endsWith('\n')) {
        html += '\n ';
    }
    
    editorHighlights.innerHTML = html;

    // 2. Build Scrollbar Markers
    const totalLines = content.split('\n').length;
    if (totalLines <= 0) return;

    const markedLines = new Set();
    const fragment = document.createDocumentFragment();

    for (const match of currentSearchResults) {
        const lineIndex = getLineIndex(content, match.start);
        if (markedLines.has(lineIndex)) continue;
        markedLines.add(lineIndex);

        const percentage = (lineIndex / totalLines) * 100;
        const marker = document.createElement('div');
        marker.className = 'scroll-marker';
        marker.style.top = `${percentage}%`;
        fragment.appendChild(marker);
    }
    scrollMarkersContainer.appendChild(fragment);
}

function refreshSearchHighlights() {
    const keyword = $('search-input').value.trim();
    const content = editorTextarea.value;
    const matches = localSearch(content, keyword);
    applySearchHighlighting(matches, keyword);
}

function scrollToMatch(match) {
    if (!match) return;
    const content = editorTextarea.value;
    const lineIndex = getLineIndex(content, match.start);
    
    const textareaStyle = window.getComputedStyle(editorTextarea);
    const lineHeight = parseFloat(textareaStyle.lineHeight) || 27.2;
    const paddingTop = parseFloat(textareaStyle.paddingTop) || 20;
    
    const targetScrollTop = lineIndex * lineHeight + paddingTop - editorTextarea.clientHeight / 2;
    
    editorTextarea.scrollTop = Math.max(0, targetScrollTop);
    
    if (editorBackdrop) {
        editorBackdrop.scrollTop = editorTextarea.scrollTop;
        editorBackdrop.scrollLeft = editorTextarea.scrollLeft;
    }
    
    editorTextarea.focus();
    editorTextarea.setSelectionRange(match.start, match.end);
}


// ═══════════════════════════════════════════════════════════════
// EVENT HANDLERS
// ═══════════════════════════════════════════════════════════════

// ── Textarea input (typing detection) ─────────────────────────
editorTextarea.addEventListener('input', () => {
    if (isUpdatingTextarea) return;

    const newContent = editorTextarea.value;
    const change = detectChange(previousContent, newContent);
    if (!change) return;

    previousContent = newContent;
    enqueueEditorChange(change);
    refreshSearchHighlights();
});

// ── Prevent browser-native Ctrl+Z / Ctrl+Y ───────────────────
editorTextarea.addEventListener('keydown', async (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !e.shiftKey) {
        e.preventDefault();
        const data = await apiEditor({ action: 'undo' });
        updateUI(data);
    } else if ((e.ctrlKey || e.metaKey) && (e.key === 'y' || (e.key === 'z' && e.shiftKey))) {
        e.preventDefault();
        const data = await apiEditor({ action: 'redo' });
        updateUI(data);
    }
});

// ── Button: Insert ────────────────────────────────────────────
$('btn-insert').addEventListener('click', () => {
    showModal('Insert Text', [
        { label: 'Position', id: 'modal-position', type: 'number', value: editorTextarea.selectionStart },
        { label: 'Text to insert', id: 'modal-text', type: 'text', value: '' }
    ], async (values) => {
        const data = await apiEditor({
            action: 'insert',
            position: parseInt(values['modal-position']) || 0,
            text: values['modal-text'] || '',
            fromTyping: false
        });
        updateUI(data);
    });
});

// ── Button: Delete ────────────────────────────────────────────
$('btn-delete').addEventListener('click', () => {
    const selStart = editorTextarea.selectionStart;
    const selEnd   = editorTextarea.selectionEnd;
    const selLen   = selEnd - selStart;

    showModal('Delete Text', [
        { label: 'Position', id: 'modal-position', type: 'number', value: selStart },
        { label: 'Length', id: 'modal-length', type: 'number', value: selLen > 0 ? selLen : 1 }
    ], async (values) => {
        const data = await apiEditor({
            action: 'delete',
            position: parseInt(values['modal-position']) || 0,
            length: parseInt(values['modal-length']) || 1
        });
        updateUI(data);
    });
});

// ── Button: Replace ───────────────────────────────────────────
$('btn-replace').addEventListener('click', () => {
    const selStart  = editorTextarea.selectionStart;
    const selEnd    = editorTextarea.selectionEnd;
    const selLen    = selEnd - selStart;
    const selText   = editorTextarea.value.substring(selStart, selEnd);

    showModal('Replace Text', [
        { label: 'Position', id: 'modal-position', type: 'number', value: selStart },
        { label: 'Length to replace', id: 'modal-length', type: 'number', value: selLen > 0 ? selLen : 1 },
        { label: 'New text', id: 'modal-newtext', type: 'text', value: '' }
    ], async (values) => {
        const data = await apiEditor({
            action: 'replace',
            position: parseInt(values['modal-position']) || 0,
            length: parseInt(values['modal-length']) || 1,
            newText: values['modal-newtext'] || ''
        });
        updateUI(data);
    });
});

// ── Button: Undo / Redo ───────────────────────────────────────
$('btn-undo').addEventListener('click', async () => {
    const data = await apiEditor({ action: 'undo' });
    updateUI(data);
});

$('btn-redo').addEventListener('click', async () => {
    const data = await apiEditor({ action: 'redo' });
    updateUI(data);
});

// ── Button: Save (Download as .txt file) ──────────────────────
$('btn-save').addEventListener('click', async () => {
    try {
        await waitForEditorQueue();
        const content = editorTextarea.value;
        const blob = new Blob([content], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'document_' + new Date().toISOString().slice(0, 10) + '.txt';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        showStatus('Document downloaded as .txt file');
    } catch (e) {
        console.error('Save error:', e);
        showStatus('Save failed.');
    }
});

// ── Button: Load (Upload .txt file) ───────────────────────────
$('btn-load').addEventListener('click', () => {
    const fileInput = document.createElement('input');
    fileInput.type = 'file';
    fileInput.accept = '.txt,text/plain';
    fileInput.addEventListener('change', async (e) => {
        const file = e.target.files[0];
        if (!file) return;

        try {
            const text = await file.text();

            // Update textarea directly
            isUpdatingTextarea = true;
            editorTextarea.value = text;
            previousContent = text;
            isUpdatingTextarea = false;

            showStatus('Loaded "' + file.name + '" (' + text.length + ' chars)');

            // Sync to backend editor state
            try {
                const data = await apiEditor({ action: 'load', content: text });
                if (data) {
                    if (data.undoCount !== undefined) statusUndoCount.textContent = data.undoCount;
                    if (data.redoCount !== undefined) statusRedoCount.textContent = data.redoCount;
                    if (data.memoryUsage !== undefined) statusMemory.textContent = formatBytes(data.memoryUsage);
                    $('btn-undo').disabled = !data.canUndo;
                    $('btn-redo').disabled = !data.canRedo;
                    if (data.history) renderHistory(data.history);
                }
            } catch (syncErr) {
                console.warn('Backend sync after load failed:', syncErr);
            }

            refreshSearchHighlights();
        } catch (readErr) {
            console.error('File read error:', readErr);
            showStatus('Failed to read file.');
        }
    });
    fileInput.click();
});

// ── Button: Search ────────────────────────────────────────────
$('btn-search').addEventListener('click', async () => {
    const keyword = $('search-input').value.trim();
    const data = await apiEditor({ action: 'search', keyword });
    updateUI(data);
    if (data.searchResults && data.searchResults.length > 0) {
        scrollToMatch(data.searchResults[0]);
    }
});

$('search-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') $('btn-search').click();
});

// Live search highlighting as the user types
$('search-input').addEventListener('input', () => {
    refreshSearchHighlights();
});

// ── Theme Toggle ──────────────────────────────────────────────
$('btn-theme').addEventListener('click', () => {
    document.body.classList.toggle('dark');
    const isDark = document.body.classList.contains('dark');
    $('btn-theme').textContent = isDark ? '\u2600' : '\uD83C\uDF19'; // ☀ vs 🌙
    localStorage.setItem('theme', isDark ? 'dark' : 'light');
});

// Apply saved theme on load
(function applyTheme() {
    const saved = localStorage.getItem('theme');
    if (saved === 'dark') {
        document.body.classList.add('dark');
        $('btn-theme').textContent = '\u2600';
    }
})();

// ── Model Selector ────────────────────────────────────────────
modelSelect.addEventListener('change', async () => {
    const data = await apiEditor({ action: 'switchModel', model: modelSelect.value });
    updateUI(data);
});

// ═══════════════════════════════════════════════════════════════
// BENCHMARK
// ═══════════════════════════════════════════════════════════════

async function runBenchmark(rq) {
    benchmarkResults.innerHTML = '<p><span class="spinner"></span>Running ' + rq + ' benchmark... Please wait.</p>';
    benchmarkDesc.textContent = '';

    try {
        const data = await apiBenchmark(rq);
        if (data.success) {
            benchmarkDesc.textContent = data.description || '';
            lastBenchmarkResults = data.results;
            lastBenchmarkRQ = rq;
            renderBenchmarkTable(rq, data.results);
            showStatus('Benchmark completed');
            btnExport.disabled = false;
        } else {
            benchmarkResults.innerHTML = '<p style="color:var(--danger);">Error: ' + escapeHtml(data.message) + '</p>';
        }
    } catch (e) {
        benchmarkResults.innerHTML = '<p style="color:var(--danger);">Network error: ' + escapeHtml(e.message) + '</p>';
    }
}

function renderBenchmarkTable(rq, results) {
    if (!results || results.length === 0) {
        benchmarkResults.innerHTML = '<p>No results.</p>';
        return;
    }

    let columns;
    if (rq === 'RQ1') {
        columns = [
            { key: 'modelName',        label: 'Model' },
            { key: 'operationCount',   label: 'Ops' },
            { key: 'memoryUsageMB',    label: 'Memory (MB)' },
            { key: 'undoLevelsRetained', label: 'Undo Levels' },
            { key: 'evictedCount',     label: 'Evicted' },
            { key: 'notes',            label: 'Notes' }
        ];
    } else if (rq === 'RQ2') {
        columns = [
            { key: 'modelName',        label: 'Model' },
            { key: 'operationCount',   label: 'Ops' },
            { key: 'memoryUsageMB',    label: 'Memory (MB)' },
            { key: 'underMemoryLimit', label: 'Under 50MB?' },
            { key: 'undoLevelsRetained', label: 'Undo Levels' },
            { key: 'notes',            label: 'Notes' }
        ];
    } else if (rq === 'RQ3') {
        columns = [
            { key: 'modelName',        label: 'Model' },
            { key: 'operationCount',   label: 'Ops' },
            { key: 'avgUndoTimeMs',    label: 'Avg Undo (ms)' },
            { key: 'avgRedoTimeMs',    label: 'Avg Redo (ms)' },
            { key: 'totalTimeMs',      label: 'Total (ms)' },
            { key: 'notes',            label: 'Notes' }
        ];
    } else {
        // Generic
        columns = Object.keys(results[0]).map(k => ({ key: k, label: k }));
    }

    let html = '<table class="benchmark-table"><thead><tr>';
    for (const col of columns) {
        html += '<th>' + escapeHtml(col.label) + '</th>';
    }
    html += '</tr></thead><tbody>';

    for (const row of results) {
        html += '<tr>';
        for (const col of columns) {
            let val = row[col.key];
            let cls = '';
            if (col.key === 'underMemoryLimit') {
                val = val === true ? 'YES' : val === false ? 'NO' : val;
                cls = val === 'YES' ? 'pass' : 'fail';
            }
            html += '<td class="' + cls + '">' + escapeHtml(String(val != null ? val : '-')) + '</td>';
        }
        html += '</tr>';
    }

    html += '</tbody></table>';
    benchmarkResults.innerHTML = html;
}

$('btn-rq1').addEventListener('click', () => runBenchmark('RQ1'));
$('btn-rq2').addEventListener('click', () => runBenchmark('RQ2'));
$('btn-rq3').addEventListener('click', () => runBenchmark('RQ3'));

// ── Export Benchmark Results ──────────────────────────────────
btnExport.addEventListener('click', () => {
    if (!lastBenchmarkResults) return;

    let csv = '';
    const keys = Object.keys(lastBenchmarkResults[0]);
    csv += keys.join(',') + '\n';
    for (const row of lastBenchmarkResults) {
        csv += keys.map(k => {
            let v = String(row[k] != null ? row[k] : '');
            // Escape commas and quotes in CSV
            if (v.includes(',') || v.includes('"') || v.includes('\n')) {
                v = '"' + v.replace(/"/g, '""') + '"';
            }
            return v;
        }).join(',') + '\n';
    }

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'benchmark_' + lastBenchmarkRQ + '_' + new Date().toISOString().slice(0, 10) + '.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    showStatus('Benchmark results exported as CSV');
});

// ═══════════════════════════════════════════════════════════════
// MODAL
// ═══════════════════════════════════════════════════════════════

let currentModalCallback = null;

function showModal(title, fields, onConfirm) {
    modalTitle.textContent = title;
    let html = '';
    for (const f of fields) {
        html += `<label for="${f.id}">${f.label}</label>`;
        html += `<input type="${f.type}" id="${f.id}" value="${f.value !== undefined ? f.value : ''}" />`;
    }
    modalBody.innerHTML = html;
    currentModalCallback = onConfirm;
    modalOverlay.classList.remove('hidden');

    // Focus first input
    const firstInput = modalBody.querySelector('input');
    if (firstInput) setTimeout(() => firstInput.focus(), 50);
}

function hideModal() {
    modalOverlay.classList.add('hidden');
    currentModalCallback = null;
}

modalConfirm.addEventListener('click', () => {
    if (currentModalCallback) {
        const inputs = modalBody.querySelectorAll('input');
        const values = {};
        inputs.forEach(inp => { values[inp.id] = inp.value; });
        currentModalCallback(values);
    }
    hideModal();
});

modalCancel.addEventListener('click', hideModal);
modalClose.addEventListener('click', hideModal);

modalOverlay.addEventListener('click', (e) => {
    if (e.target === modalOverlay) hideModal();
});

// Close modal on Escape
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && !modalOverlay.classList.contains('hidden')) {
        hideModal();
    }
});

// ═══════════════════════════════════════════════════════════════
// INIT — Load initial state from server
// ═══════════════════════════════════════════════════════════════

(async function init() {
    try {
        const data = await apiEditor({ action: 'getState' });
        updateUI(data);
    } catch (e) {
        showStatus('Failed to connect to server.');
    }
})();

// ── Scroll & Resize Synchronization ───────────────────────────
editorTextarea.addEventListener('scroll', () => {
    if (editorBackdrop) {
        editorBackdrop.scrollTop = editorTextarea.scrollTop;
        editorBackdrop.scrollLeft = editorTextarea.scrollLeft;
    }
});

if (window.ResizeObserver) {
    const resizeObserver = new ResizeObserver(() => {
        if (editorHighlights && editorTextarea) {
            editorHighlights.style.width = editorTextarea.clientWidth + 'px';
        }
    });
    resizeObserver.observe(editorTextarea);
}

<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="CSD201 Undo/Redo Text Editor Simulator — A web-based demonstration of Stack, Command Pattern, and data-structure-driven undo/redo history models.">
    <title>CSD201 — Undo/Redo Text Editor</title>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="styles.css">
</head>
<body>

<!-- ═══════════════════════════════════════════════════════════════
     HEADER
     ═══════════════════════════════════════════════════════════════ -->
<header id="app-header">
    <div class="header-left">
        <h1>
            <span class="logo-icon">&#9998;</span>
            CSD201 Undo/Redo Editor
        </h1>
        <span class="badge">Data Structures Project</span>
    </div>
    <div class="header-right">
        <label class="model-select-label" for="model-select">History Model:</label>
        <select id="model-select">
            <option value="CommandHistory" selected>CommandHistory (RQ2)</option>
            <option value="SnapshotHistory">SnapshotHistory (RQ2)</option>
            <option value="BoundedStackHistory">BoundedStackHistory (RQ1)</option>
            <option value="LRUStackHistory">LRUStackHistory (RQ1)</option>
            <option value="TwoStackHistory">TwoStackHistory (RQ3)</option>
            <option value="DequeHistory">DequeHistory (RQ3)</option>
        </select>
        <button id="btn-theme" class="btn btn-icon" title="Toggle Dark Mode">&#127769;</button>
    </div>
</header>

<!-- ═══════════════════════════════════════════════════════════════
     STATUS BAR
     ═══════════════════════════════════════════════════════════════ -->
<div id="status-bar">
    <span id="status-message">Ready</span>
    <span id="status-meta">
        Memory: <span id="status-memory">0 B</span> &middot;
        Undo: <span id="status-undo-count">0</span> &middot;
        Redo: <span id="status-redo-count">0</span>
    </span>
</div>

<!-- ═══════════════════════════════════════════════════════════════
     MAIN CONTENT
     ═══════════════════════════════════════════════════════════════ -->
<main id="main-content">

    <!-- ── LEFT PANEL: EDITOR ─────────────────────────────────── -->
    <section id="editor-panel" class="panel">
        <div class="panel-header">
            <h2>Text Editor</h2>
        </div>
        <div class="editor-wrapper">
            <div class="editor-container">
                <div id="editor-backdrop" class="editor-backdrop">
                    <div id="editor-highlights" class="editor-highlights"></div>
                </div>
                <textarea id="editor-textarea"
                          placeholder="Start typing here... Your keystrokes are tracked as Operations."
                          spellcheck="false"></textarea>
                <div id="scroll-markers-container" class="scroll-markers-container"></div>
            </div>
        </div>

        <!-- Toolbar -->
        <div class="toolbar">
            <div class="toolbar-group">
                <button id="btn-insert" class="btn btn-primary" title="Insert text at position">
                    <span class="btn-icon-inline">&#43;</span> Insert
                </button>
                <button id="btn-delete" class="btn btn-danger" title="Delete text at position">
                    <span class="btn-icon-inline">&#8722;</span> Delete
                </button>
                <button id="btn-replace" class="btn btn-warning" title="Replace text at position">
                    <span class="btn-icon-inline">&#8644;</span> Replace
                </button>
            </div>
            <div class="toolbar-group">
                <button id="btn-undo" class="btn btn-secondary" title="Undo (Ctrl+Z)">
                    &#8630; Undo
                </button>
                <button id="btn-redo" class="btn btn-secondary" title="Redo (Ctrl+Y)">
                    &#8631; Redo
                </button>
            </div>
            <div class="toolbar-group">
                <button id="btn-save" class="btn btn-outline" title="Save document">
                    &#128190; Save
                </button>
                <button id="btn-load" class="btn btn-outline" title="Load document">
                    &#128194; Load
                </button>
            </div>
            <div class="toolbar-group search-group">
                <input id="search-input" type="text" placeholder="Search..." />
                <button id="btn-search" class="btn btn-outline" title="Search text">
                    &#128269; Search
                </button>
            </div>
        </div>
    </section>

    <!-- ── RIGHT PANEL: HISTORY VIEWER ────────────────────────── -->
    <section id="history-panel" class="panel">
        <div class="panel-header">
            <h2>Action History</h2>
            <span id="history-count" class="count-badge">0 actions</span>
        </div>
        <div class="history-section-header">Undo List (Active)</div>
        <div id="history-list" class="history-list">
            <div class="history-empty">No actions yet. Start typing or use the buttons.</div>
        </div>
        <div class="history-section-header">Redo List (Undone)</div>
        <div id="redo-list" class="history-list">
            <div class="history-empty">No undone actions.</div>
        </div>
    </section>

</main>

<!-- ═══════════════════════════════════════════════════════════════
     BENCHMARK PANEL
     ═══════════════════════════════════════════════════════════════ -->
<section id="benchmark-panel" class="panel">
    <div class="panel-header">
        <h2>&#128202; Benchmark</h2>
    </div>
    <div class="benchmark-controls">
        <button id="btn-rq1" class="btn btn-accent">Run RQ1</button>
        <button id="btn-rq2" class="btn btn-accent">Run RQ2</button>
        <button id="btn-rq3" class="btn btn-accent">Run RQ3</button>
        <button id="btn-export" class="btn btn-outline" disabled>&#128229; Export Results</button>
    </div>
    <p id="benchmark-description" class="benchmark-desc"></p>
    <div id="benchmark-results" class="benchmark-results">
        <p class="benchmark-placeholder">Click a benchmark button above to run.</p>
    </div>
</section>

<!-- ═══════════════════════════════════════════════════════════════
     MODAL (for Insert / Delete / Replace parameters)
     ═══════════════════════════════════════════════════════════════ -->
<div id="modal-overlay" class="modal-overlay hidden">
    <div class="modal">
        <div class="modal-header">
            <h3 id="modal-title">Operation</h3>
            <button id="modal-close" class="btn btn-icon">&times;</button>
        </div>
        <div id="modal-body" class="modal-body">
            <!-- Dynamic form fields injected by JS -->
        </div>
        <div class="modal-footer">
            <button id="modal-cancel" class="btn btn-outline">Cancel</button>
            <button id="modal-confirm" class="btn btn-primary">Apply</button>
        </div>
    </div>
</div>

<script src="js/app.js?v=save-load-20260615"></script>
</body>
</html>

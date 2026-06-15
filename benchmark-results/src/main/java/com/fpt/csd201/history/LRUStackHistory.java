package com.fpt.csd201.history;

import com.fpt.csd201.editor.Operation;
import com.fpt.csd201.editor.TextEditor;
import java.util.ArrayList;
import java.util.List;

/**
 * LRUStackHistory — Evicts oldest operations when memory exceeds maxHistoryBytes.
 *
 * Used for RQ1 comparison against BoundedStackHistory.
 *
 * Key difference from BoundedStackHistory:
 *   - BoundedStackHistory evicts by COUNT (fixed limit N).
 *   - LRUStackHistory evicts by MEMORY (when total bytes exceed threshold).
 *
 * Default maxHistoryBytes: 50 MB (50 × 1024 × 1024).
 */
public class LRUStackHistory implements HistoryStrategy {
    private static final long serialVersionUID = 1L;

    private final ArrayList<Operation> undoHistory;
    private final ArrayList<Operation> redoHistory;
    private long maxHistoryBytes;
    private long currentHistoryBytes;
    private int evictedCount;

    public LRUStackHistory() {
        this(50L * 1024 * 1024); // 50 MB default
    }

    public LRUStackHistory(long maxHistoryBytes) {
        this.undoHistory = new ArrayList<>();
        this.redoHistory = new ArrayList<>();
        this.maxHistoryBytes = maxHistoryBytes;
        this.currentHistoryBytes = 0;
        this.evictedCount = 0;
    }

    /**
     * Record a new operation:
     * 1. Execute on editor.
     * 2. Add to back of undoHistory.
     * 3. Evict oldest from front if memory exceeds threshold.
     * 4. Clear redo history.
     */
    @Override
    public void record(Operation op, TextEditor editor) {
        op.execute(editor);
        undoHistory.add(op);
        currentHistoryBytes += op.estimatedBytes();
        evictIfNeeded();
        clearRedo();
    }

    /**
     * Evict oldest operations from the front until memory is under the limit.
     */
    private void evictIfNeeded() {
        while (currentHistoryBytes > maxHistoryBytes && !undoHistory.isEmpty()) {
            Operation evicted = undoHistory.remove(0);
            currentHistoryBytes -= evicted.estimatedBytes();
            evictedCount++;
        }
    }

    @Override
    public boolean undo(TextEditor editor) {
        if (undoHistory.isEmpty()) {
            return false;
        }
        Operation op = undoHistory.remove(undoHistory.size() - 1);
        op.undo(editor);
        currentHistoryBytes -= op.estimatedBytes();
        redoHistory.add(op);
        return true;
    }

    @Override
    public boolean redo(TextEditor editor) {
        if (redoHistory.isEmpty()) {
            return false;
        }
        Operation op = redoHistory.remove(redoHistory.size() - 1);
        op.execute(editor);
        undoHistory.add(op);
        currentHistoryBytes += op.estimatedBytes();
        return true;
    }

    private void clearRedo() {
        redoHistory.clear();
    }

    @Override
    public long getMemoryUsage() {
        return currentHistoryBytes;
    }

    @Override
    public List<Operation> getActionHistory() {
        return new ArrayList<>(undoHistory);
    }

    @Override
    public Operation peekUndo() {
        return undoHistory.isEmpty() ? null : undoHistory.get(undoHistory.size() - 1);
    }

    @Override
    public Operation peekRedo() {
        return redoHistory.isEmpty() ? null : redoHistory.get(redoHistory.size() - 1);
    }

    @Override
    public int getUndoCount() {
        return undoHistory.size();
    }

    @Override
    public int getRedoCount() {
        return redoHistory.size();
    }

    public long getMaxHistoryBytes() {
        return maxHistoryBytes;
    }

    public int getEvictedCount() {
        return evictedCount;
    }

    @Override
    public String getModelName() {
        return "LRUStackHistory (maxMB=" + (maxHistoryBytes / (1024 * 1024)) + ")";
    }

    @Override
    public List<Operation> getRedoActionHistory() {
        return new ArrayList<>(redoHistory);
    }
}

package com.fpt.csd201.history;

import com.fpt.csd201.editor.Operation;
import com.fpt.csd201.editor.TextEditor;
import java.util.ArrayList;
import java.util.List;

/**
 * BoundedStackHistory — Limits undo history to a fixed count N.
 *
 * Used for RQ1 comparison against LRUStackHistory.
 *
 * Internal structure: uses an ArrayList to simulate a deque.
 *   - add() to push newest operation at the back
 *   - remove(size-1) to undo newest operation (stack-like LIFO)
 *   - remove(0) to evict oldest operation from the front
 *
 * Even though the internal structure is a deque/array, the undo
 * behavior is still stack-like because undo always removes the
 * newest operation.
 *
 * Default limit: 500 operations.
 */
public class BoundedStackHistory implements HistoryStrategy {
    private static final long serialVersionUID = 1L;

    private final ArrayList<Operation> undoHistory;
    private final ArrayList<Operation> redoHistory;
    private final int limit;
    private long currentHistoryBytes;
    private int evictedCount;

    public BoundedStackHistory() {
        this(500);
    }

    public BoundedStackHistory(int limit) {
        this.undoHistory = new ArrayList<>();
        this.redoHistory = new ArrayList<>();
        this.limit = limit;
        this.currentHistoryBytes = 0;
        this.evictedCount = 0;
    }

    /**
     * Record a new operation:
     * 1. Execute on editor.
     * 2. Push to back of undoHistory.
     * 3. Evict oldest if count exceeds limit.
     * 4. Clear redo history.
     */
    @Override
    public void record(Operation op, TextEditor editor) {
        op.execute(editor);
        undoHistory.add(op);
        currentHistoryBytes += op.estimatedBytes();
        evictOldestIfNeeded();
        clearRedo();
    }

    /**
     * Evict the oldest operation from the front if the count exceeds the limit.
     */
    private void evictOldestIfNeeded() {
        while (undoHistory.size() > limit) {
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
        // Remove newest from back (stack-like LIFO)
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

    public int getLimit() {
        return limit;
    }

    public int getEvictedCount() {
        return evictedCount;
    }

    @Override
    public String getModelName() {
        return "BoundedStackHistory (limit=" + limit + ")";
    }

    @Override
    public List<Operation> getRedoActionHistory() {
        return new ArrayList<>(redoHistory);
    }
}

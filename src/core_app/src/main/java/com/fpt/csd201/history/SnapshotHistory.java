package com.fpt.csd201.history;

import com.fpt.csd201.editor.Operation;
import com.fpt.csd201.editor.TextEditor;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * SnapshotHistory — Stores full text snapshots instead of Operation objects.
 *
 * Used for RQ2 comparison with CommandHistory.
 *
 * Key difference:
 *   - CommandHistory stores lightweight Operation objects (type, position, text fragments).
 *   - SnapshotHistory stores the ENTIRE editor content after every operation.
 *   This makes SnapshotHistory much more memory-intensive on large documents.
 *
 * Behavior:
 *   record(op) → save current content snapshot (before), execute op
 *   undo()     → push current content to redoSnapshots, restore previous snapshot
 *   redo()     → push current content to undoSnapshots, restore next snapshot
 */
public class SnapshotHistory implements HistoryStrategy {
    private static final long serialVersionUID = 1L;

    private final Stack<String> undoSnapshots;
    private final Stack<String> redoSnapshots;

    /**
     * We also keep a parallel list of Operation descriptions
     * so the history viewer can show meaningful action labels.
     */
    private final Stack<Operation> operationLabels;

    public SnapshotHistory() {
        this.undoSnapshots = new Stack<>();
        this.redoSnapshots = new Stack<>();
        this.operationLabels = new Stack<>();
    }

    /**
     * Record an operation:
     * 1. Save current editor content as a snapshot (BEFORE the operation).
     * 2. Execute the operation.
     * 3. Clear redo history.
     */
    @Override
    public void record(Operation op, TextEditor editor) {
        // Save snapshot BEFORE executing the operation
        undoSnapshots.push(editor.getContent());
        operationLabels.push(op);

        // Execute the operation
        op.execute(editor);

        // New operation invalidates redo history
        redoSnapshots.clear();
    }

    /**
     * Convenience method to record a raw snapshot (used by benchmark).
     */
    public void recordSnapshot(String content) {
        undoSnapshots.push(content);
    }

    /**
     * Undo: restore the previous snapshot.
     * Push current content to redo side so we can redo later.
     */
    @Override
    public boolean undo(TextEditor editor) {
        if (undoSnapshots.isEmpty()) {
            return false;
        }
        // Push current content to redo
        redoSnapshots.push(editor.getContent());

        // Restore previous snapshot
        String previousContent = undoSnapshots.pop();
        editor.setContent(previousContent);
        return true;
    }

    /**
     * Redo: restore the next (redo) snapshot.
     * Push current content back to undo side.
     */
    @Override
    public boolean redo(TextEditor editor) {
        if (redoSnapshots.isEmpty()) {
            return false;
        }
        // Push current content to undo
        undoSnapshots.push(editor.getContent());

        // Restore redo snapshot
        String nextContent = redoSnapshots.pop();
        editor.setContent(nextContent);
        return true;
    }

    /**
     * Memory usage: sum of all stored snapshot string lengths × 2 (UTF-16).
     */
    @Override
    public long getMemoryUsage() {
        long total = 0;
        for (String snapshot : undoSnapshots) {
            total += (long) snapshot.length() * 2;
        }
        for (String snapshot : redoSnapshots) {
            total += (long) snapshot.length() * 2;
        }
        return total;
    }

    @Override
    public List<Operation> getActionHistory() {
        return new ArrayList<>(operationLabels);
    }

    @Override
    public Operation peekUndo() {
        return operationLabels.isEmpty() ? null : operationLabels.peek();
    }

    @Override
    public Operation peekRedo() {
        // SnapshotHistory doesn't track operation labels for redo side
        return null;
    }

    @Override
    public int getUndoCount() {
        return undoSnapshots.size();
    }

    @Override
    public int getRedoCount() {
        return redoSnapshots.size();
    }

    @Override
    public String getModelName() {
        return "SnapshotHistory";
    }
}

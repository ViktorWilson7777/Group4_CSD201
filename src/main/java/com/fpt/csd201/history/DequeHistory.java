package com.fpt.csd201.history;

import com.fpt.csd201.editor.Operation;
import com.fpt.csd201.editor.TextEditor;
import java.util.ArrayList;
import java.util.List;

/**
 * DequeHistory — Single shared history list with currentIndex.
 *
 * Used for RQ3 comparison against TwoStackHistory.
 *
 * Instead of two stacks, this model maintains ONE list of operations
 * and a currentIndex pointer:
 *   - Operations before/at currentIndex are "active" (undoable)
 *   - Operations after currentIndex are "future" (would need to be
 *     discarded when a new operation is recorded)
 *
 * Behavior:
 *   record(op): if currentIndex is not at the end, remove all
 *               operations after currentIndex. Add new op. Move
 *               currentIndex to the end.
 *   undo():     undo operation at currentIndex, currentIndex--
 *   redo():     currentIndex++, execute operation at currentIndex
 */
public class DequeHistory implements HistoryStrategy {
    private static final long serialVersionUID = 1L;

    private final ArrayList<Operation> history;
    private int currentIndex;
    private long currentHistoryBytes;

    public DequeHistory() {
        this.history = new ArrayList<>();
        this.currentIndex = -1;
        this.currentHistoryBytes = 0;
    }

    /**
     * Record a new operation:
     * 1. If currentIndex is not at the latest, remove all operations after it.
     * 2. Execute and add the new operation.
     * 3. Move currentIndex to the latest.
     */
    @Override
    public void record(Operation op, TextEditor editor) {
        // Remove all "future" operations after currentIndex
        while (history.size() - 1 > currentIndex) {
            Operation removed = history.remove(history.size() - 1);
            currentHistoryBytes -= removed.estimatedBytes();
        }

        op.execute(editor);
        history.add(op);
        currentHistoryBytes += op.estimatedBytes();
        currentIndex = history.size() - 1;
    }

    /**
     * Undo: if currentIndex < 0, nothing to undo.
     * Otherwise undo the operation at currentIndex and decrement.
     */
    @Override
    public boolean undo(TextEditor editor) {
        if (currentIndex < 0) {
            return false;
        }
        Operation op = history.get(currentIndex);
        op.undo(editor);
        currentIndex--;
        return true;
    }

    /**
     * Redo: if currentIndex >= history.size() - 1, nothing to redo.
     * Otherwise increment currentIndex and execute the operation.
     */
    @Override
    public boolean redo(TextEditor editor) {
        if (currentIndex >= history.size() - 1) {
            return false;
        }
        currentIndex++;
        Operation op = history.get(currentIndex);
        op.execute(editor);
        return true;
    }

    @Override
    public long getMemoryUsage() {
        return currentHistoryBytes;
    }

    /** Return operations up to and including currentIndex (active history). */
    @Override
    public List<Operation> getActionHistory() {
        if (currentIndex < 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(history.subList(0, currentIndex + 1));
    }

    @Override
    public Operation peekUndo() {
        if (currentIndex < 0 || currentIndex >= history.size()) {
            return null;
        }
        return history.get(currentIndex);
    }

    @Override
    public Operation peekRedo() {
        if (currentIndex + 1 >= history.size()) {
            return null;
        }
        return history.get(currentIndex + 1);
    }

    @Override
    public int getUndoCount() {
        return currentIndex + 1;
    }

    @Override
    public int getRedoCount() {
        return history.size() - 1 - currentIndex;
    }

    @Override
    public String getModelName() {
        return "DequeHistory";
    }

    @Override
    public List<Operation> getRedoActionHistory() {
        List<Operation> list = new ArrayList<>();
        for (int i = history.size() - 1; i > currentIndex; i--) {
            list.add(history.get(i));
        }
        return list;
    }
}

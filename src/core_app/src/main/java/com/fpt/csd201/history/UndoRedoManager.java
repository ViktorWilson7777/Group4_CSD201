package com.fpt.csd201.history;

import com.fpt.csd201.editor.Operation;
import com.fpt.csd201.editor.TextEditor;
import java.io.Serializable;
import java.util.List;

/**
 * UndoRedoManager — Coordinator for undo/redo through a HistoryStrategy.
 *
 * This class is NOT tightly coupled to any single history implementation.
 * It delegates all history operations to the pluggable historyModel.
 *
 * Grouped undo/redo logic lives here (not in individual history models):
 *   - On undo: check groupId of the top operation; keep undoing while
 *     the next top operation shares the same groupId.
 *   - On redo: same logic for the redo stack.
 *   - Only InsertOperations can have a non-null groupId.
 *
 * undoTo(targetIndex) logic:
 *   - Do NOT remove a random operation from the middle of the stack.
 *   - Repeatedly call single undo until currentIndex reaches targetIndex.
 *   - Use while (currentUndoCount - 1 > targetIndex) because the
 *     selected action should remain active.
 */
public class UndoRedoManager implements Serializable {
    private static final long serialVersionUID = 1L;

    private HistoryStrategy historyModel;

    public UndoRedoManager(HistoryStrategy historyModel) {
        this.historyModel = historyModel;
    }

    /**
     * Record a new operation. Delegates to the history model,
     * which will execute the operation and store it.
     */
    public void recordOperation(Operation op, TextEditor editor) {
        historyModel.record(op, editor);
    }

    /**
     * Grouped undo:
     * 1. Peek at the top operation's groupId.
     * 2. Undo it.
     * 3. While the new top has the same non-null groupId, keep undoing.
     *
     * @return true if at least one operation was undone
     */
    public boolean undo(TextEditor editor) {
        Operation top = historyModel.peekUndo();
        if (top == null) {
            return false;
        }

        String groupId = top.getGroupId();
        if (!historyModel.undo(editor)) {
            return false;
        }

        // Continue undoing operations with the same groupId (group undo)
        if (groupId != null) {
            while (historyModel.peekUndo() != null
                    && groupId.equals(historyModel.peekUndo().getGroupId())) {
                historyModel.undo(editor);
            }
        }
        return true;
    }

    /**
     * Grouped redo:
     * 1. Peek at the top of the redo stack.
     * 2. Redo it.
     * 3. While the new redo top has the same non-null groupId, keep redoing.
     *
     * @return true if at least one operation was redone
     */
    public boolean redo(TextEditor editor) {
        Operation top = historyModel.peekRedo();
        if (top == null) {
            return false;
        }

        String groupId = top.getGroupId();
        if (!historyModel.redo(editor)) {
            return false;
        }

        // Continue redoing operations with the same groupId (group redo)
        if (groupId != null) {
            while (historyModel.peekRedo() != null
                    && groupId.equals(historyModel.peekRedo().getGroupId())) {
                historyModel.redo(editor);
            }
        }
        return true;
    }

    /**
     * Undo to a specific index in the action history.
     *
     * Repeatedly calls single (non-grouped) undo until the
     * number of active operations equals targetIndex + 1.
     *
     * The operation at targetIndex remains active (not undone).
     *
     * @param targetIndex 0-based index into getActionHistory()
     */
    public void undoTo(int targetIndex, TextEditor editor) {
        // Current undo count = number of active (undoable) operations
        // We want to keep targetIndex + 1 operations active.
        while (historyModel.getUndoCount() - 1 > targetIndex) {
            if (!historyModel.undo(editor)) {
                break;
            }
        }
    }

    /** Get the full action history (active operations, oldest first). */
    public List<Operation> getActionHistory() {
        return historyModel.getActionHistory();
    }

    /** Get estimated memory usage from the history model. */
    public long getMemoryUsage() {
        return historyModel.getMemoryUsage();
    }

    /** Clear the redo stack (via recording a dummy or direct access). */
    public void clearRedoStack() {
        // Clearing redo is handled internally by record() in each model.
        // This method is here for the interface contract.
    }

    public HistoryStrategy getHistoryModel() {
        return historyModel;
    }

    public void setHistoryModel(HistoryStrategy historyModel) {
        this.historyModel = historyModel;
    }

    public boolean canUndo() {
        return historyModel.getUndoCount() > 0;
    }

    public boolean canRedo() {
        return historyModel.getRedoCount() > 0;
    }
}

package com.fpt.csd201.history;

import com.fpt.csd201.editor.Operation;
import com.fpt.csd201.editor.TextEditor;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * CommandHistory — Main Command Pattern + Stack model.
 *
 * This class clearly maintains two stacks storing Operation objects:
 *   - undoStack: operations that can be undone (newest on top)
 *   - redoStack: operations that were undone and can be redone
 *
 * This is the primary model used for the editor and for RQ2 comparison
 * against SnapshotHistory.
 *
 * Behavior:
 *   record(op) → push to undoStack, clear redoStack
 *   undo()     → pop from undoStack, call op.undo(), push to redoStack
 *   redo()     → pop from redoStack, call op.execute(), push to undoStack
 */
public class CommandHistory implements HistoryStrategy {
    private static final long serialVersionUID = 1L;

    private final Stack<Operation> undoStack;
    private final Stack<Operation> redoStack;
    private long currentHistoryBytes;

    public CommandHistory() {
        this.undoStack = new Stack<>();
        this.redoStack = new Stack<>();
        this.currentHistoryBytes = 0;
    }

    /**
     * Record a new operation.
     * 1. Execute the operation on the editor.
     * 2. Push operation onto the undoStack.
     * 3. Track memory.
     * 4. Clear redoStack (new operation invalidates redo history).
     */
    @Override
    public void record(Operation op, TextEditor editor) {
        op.execute(editor);
        undoStack.push(op);
        currentHistoryBytes += op.estimatedBytes();
        clearRedoStack();
    }

    /**
     * Undo the most recent operation (single operation, not grouped).
     * Grouped undo logic is handled by UndoRedoManager.
     */
    @Override
    public boolean undo(TextEditor editor) {
        if (undoStack.isEmpty()) {
            return false;
        }
        Operation op = undoStack.pop();
        op.undo(editor);
        currentHistoryBytes -= op.estimatedBytes();
        redoStack.push(op);
        return true;
    }

    /**
     * Redo the most recently undone operation (single operation).
     * Grouped redo logic is handled by UndoRedoManager.
     */
    @Override
    public boolean redo(TextEditor editor) {
        if (redoStack.isEmpty()) {
            return false;
        }
        Operation op = redoStack.pop();
        op.execute(editor);
        currentHistoryBytes += op.estimatedBytes();
        undoStack.push(op);
        return true;
    }

    /** Clear the redo stack — called when a new operation is recorded. */
    public void clearRedoStack() {
        while (!redoStack.isEmpty()) {
            Operation op = redoStack.pop();
            // Note: these bytes were already subtracted during undo
        }
    }

    @Override
    public long getMemoryUsage() {
        return currentHistoryBytes;
    }

    /** Return all operations on the undoStack, oldest first. */
    @Override
    public List<Operation> getActionHistory() {
        return new ArrayList<>(undoStack);
    }

    @Override
    public Operation peekUndo() {
        return undoStack.isEmpty() ? null : undoStack.peek();
    }

    @Override
    public Operation peekRedo() {
        return redoStack.isEmpty() ? null : redoStack.peek();
    }

    @Override
    public int getUndoCount() {
        return undoStack.size();
    }

    @Override
    public int getRedoCount() {
        return redoStack.size();
    }

    @Override
    public String getModelName() {
        return "CommandHistory";
    }

    @Override
    public List<Operation> getRedoActionHistory() {
        return new ArrayList<>(redoStack);
    }
}

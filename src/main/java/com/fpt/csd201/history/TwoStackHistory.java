package com.fpt.csd201.history;

import com.fpt.csd201.editor.Operation;
import com.fpt.csd201.editor.TextEditor;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * TwoStackHistory — Classic two-stack design for RQ3 comparison.
 *
 * Structurally identical to CommandHistory but used as a distinct class
 * so that RQ3 can benchmark TwoStackHistory vs DequeHistory independently.
 *
 * Uses java.util.Stack (extends Vector, synchronized).
 */
public class TwoStackHistory implements HistoryStrategy {
    private static final long serialVersionUID = 1L;

    private final Stack<Operation> undoStack;
    private final Stack<Operation> redoStack;
    private long currentHistoryBytes;

    public TwoStackHistory() {
        this.undoStack = new Stack<>();
        this.redoStack = new Stack<>();
        this.currentHistoryBytes = 0;
    }

    @Override
    public void record(Operation op, TextEditor editor) {
        op.execute(editor);
        undoStack.push(op);
        currentHistoryBytes += op.estimatedBytes();
        clearRedo();
    }

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

    private void clearRedo() {
        redoStack.clear();
    }

    @Override
    public long getMemoryUsage() {
        return currentHistoryBytes;
    }

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
        return "TwoStackHistory";
    }

    @Override
    public List<Operation> getRedoActionHistory() {
        return new ArrayList<>(redoStack);
    }
}

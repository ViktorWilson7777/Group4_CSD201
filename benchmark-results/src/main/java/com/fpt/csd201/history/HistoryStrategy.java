package com.fpt.csd201.history;

import com.fpt.csd201.editor.Operation;
import com.fpt.csd201.editor.TextEditor;
import java.io.Serializable;
import java.util.List;

/**
 * HistoryStrategy — Common interface for all history models.
 *
 * All history models must implement this interface so that
 * UndoRedoManager and BenchmarkRunner can test them fairly
 * with the same operations and the same editor setup.
 *
 * Implementations:
 *   - CommandHistory     (Command Pattern + Stack — main model)
 *   - SnapshotHistory    (full text snapshots)
 *   - BoundedStackHistory(fixed-limit N eviction)
 *   - LRUStackHistory    (memory-based eviction)
 *   - TwoStackHistory    (classic two-stack for RQ3)
 *   - DequeHistory       (single deque + currentIndex for RQ3)
 */
public interface HistoryStrategy extends Serializable {

    /**
     * Record a new operation.
     * The implementation executes the operation on the editor,
     * stores whatever it needs (operation object or snapshot),
     * and clears the redo history.
     */
    void record(Operation op, TextEditor editor);

    /** Undo the most recent operation. Returns false if nothing to undo. */
    boolean undo(TextEditor editor);

    /** Redo the most recently undone operation. Returns false if nothing to redo. */
    boolean redo(TextEditor editor);

    /** Estimated memory usage of stored history data (in bytes). */
    long getMemoryUsage();

    /** Return a list of operations on the undo stack (oldest first). */
    List<Operation> getActionHistory();

    /** Peek at the top of the undo stack without removing. Returns null if empty. */
    Operation peekUndo();

    /** Peek at the top of the redo stack without removing. Returns null if empty. */
    Operation peekRedo();

    /** Number of undoable operations. */
    int getUndoCount();

    /** Number of redoable operations. */
    int getRedoCount();

    /** Get the name of this history model for display / benchmark. */
    String getModelName();

    /** Return a list of operations on the redo stack (or equivalent). */
    List<Operation> getRedoActionHistory();
}

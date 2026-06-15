package com.fpt.csd201.editor;

/**
 * DeleteOperation — Deletes oldText starting at position.
 * Undo re-inserts oldText at the same position.
 *
 * Delete operations are NEVER grouped (groupId is always null).
 */
public class DeleteOperation extends Operation {
    private static final long serialVersionUID = 1L;

    /**
     * @param position character index where deletion starts
     * @param oldText  the text that was deleted
     */
    public DeleteOperation(int position, String oldText) {
        super("DELETE", position, oldText, "", null);
    }

    /** Execute: delete oldText from position. */
    @Override
    public void execute(TextEditor editor) {
        editor.deleteText(position, oldText.length());
    }

    /** Undo: insert oldText back at position. */
    @Override
    public void undo(TextEditor editor) {
        editor.insertText(position, oldText);
    }
}

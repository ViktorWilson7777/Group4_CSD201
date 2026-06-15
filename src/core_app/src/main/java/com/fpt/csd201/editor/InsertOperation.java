package com.fpt.csd201.editor;

/**
 * InsertOperation — Inserts newText at the given position.
 * Undo removes the inserted text.
 */
public class InsertOperation extends Operation {
    private static final long serialVersionUID = 1L;

    /**
     * @param position character index where text is inserted
     * @param newText  text to insert
     * @param groupId  optional group identifier for grouped undo
     */
    public InsertOperation(int position, String newText, String groupId) {
        super("INSERT", position, "", newText, groupId);
    }

    /** Execute: insert newText at position. */
    @Override
    public void execute(TextEditor editor) {
        editor.insertText(position, newText);
    }

    /** Undo: delete the inserted text from position. */
    @Override
    public void undo(TextEditor editor) {
        editor.deleteText(position, newText.length());
    }
}

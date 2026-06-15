package com.fpt.csd201.editor;

/**
 * ReplaceOperation — Replaces oldText with newText at position.
 * Undo restores oldText at the same position.
 *
 * Replace operations are NEVER grouped (groupId is always null).
 */
public class ReplaceOperation extends Operation {
    private static final long serialVersionUID = 1L;

    /**
     * @param position character index where replacement starts
     * @param oldText  the original text being replaced
     * @param newText  the replacement text
     */
    public ReplaceOperation(int position, String oldText, String newText) {
        super("REPLACE", position, oldText, newText, null);
    }

    /** Execute: replace oldText with newText at position. */
    @Override
    public void execute(TextEditor editor) {
        editor.replaceText(position, oldText.length(), newText);
    }

    /** Undo: restore oldText at position (replace newText back to oldText). */
    @Override
    public void undo(TextEditor editor) {
        editor.replaceText(position, newText.length(), oldText);
    }
}

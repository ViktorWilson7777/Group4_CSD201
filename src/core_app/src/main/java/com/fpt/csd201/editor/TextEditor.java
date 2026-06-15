package com.fpt.csd201.editor;

import java.io.Serializable;

/**
 * TextEditor — Stores and updates current text content.
 *
 * This class does NOT manage undoStack or redoStack.
 * That responsibility belongs to the HistoryStrategy implementations.
 */
public class TextEditor implements Serializable {
    private static final long serialVersionUID = 1L;

    private String content;

    public TextEditor() {
        this.content = "";
    }

    /**
     * Insert text at a specific position.
     * @param position character index (clamped to valid range)
     * @param text     text to insert
     */
    public void insertText(int position, String text) {
        int pos = Math.max(0, Math.min(position, content.length()));
        content = content.substring(0, pos) + text + content.substring(pos);
    }

    /**
     * Delete a number of characters starting at position.
     * @param position start index (clamped)
     * @param length   number of characters to delete (clamped)
     */
    public void deleteText(int position, int length) {
        int pos = Math.max(0, Math.min(position, content.length()));
        int len = Math.min(length, content.length() - pos);
        content = content.substring(0, pos) + content.substring(pos + len);
    }

    /**
     * Replace characters at position with new text.
     * @param position start index (clamped)
     * @param length   number of characters to replace (clamped)
     * @param newText  replacement text
     */
    public void replaceText(int position, int length, String newText) {
        int pos = Math.max(0, Math.min(position, content.length()));
        int len = Math.min(length, content.length() - pos);
        content = content.substring(0, pos) + newText + content.substring(pos + len);
    }

    /** Get the current content. */
    public String getContent() {
        return content;
    }

    /** Set the entire content (used by load, snapshot restore, etc.). */
    public void setContent(String content) {
        this.content = content != null ? content : "";
    }
}

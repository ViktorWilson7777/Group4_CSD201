package com.fpt.csd201.editor;

import java.io.Serializable;

/**
 * Operation — Abstract base class (Command Pattern).
 *
 * Each Operation stores enough data to execute (apply) a change
 * and to undo (reverse) it on a TextEditor instance.
 *
 * - oldText is used to restore previous content during undo.
 * - newText is used to reapply the change during redo.
 * - timestamp is used for Action History and group-action timing.
 * - groupId is used to group continuous typing operations into
 *   one grouped undo action (InsertOperation only).
 */
public abstract class Operation implements Serializable {
    private static final long serialVersionUID = 1L;

    protected String type;
    protected int position;
    protected String oldText;
    protected String newText;
    protected long timestamp;
    protected String groupId;

    /**
     * @param type     "INSERT", "DELETE", or "REPLACE"
     * @param position character index in the text
     * @param oldText  text that existed before the operation
     * @param newText  text placed by the operation
     * @param groupId  optional group identifier for grouped undo
     */
    public Operation(String type, int position, String oldText, String newText, String groupId) {
        this.type = type;
        this.position = position;
        this.oldText = oldText != null ? oldText : "";
        this.newText = newText != null ? newText : "";
        this.timestamp = System.currentTimeMillis(); // nhìn vào cái đồng hồ trên máy tính/server đang chạy code Java, lưu thời gian bằng con số mili-giây khổng lồ (VD: 1718428859000)
        this.groupId = groupId;
    }

    /** Apply this operation to the editor. */
    public abstract void execute(TextEditor editor);

    /** Reverse this operation on the editor. */
    public abstract void undo(TextEditor editor);

    /**
     * Estimate memory footprint of this operation in bytes.
     * Each Java char ≈ 2 bytes (UTF-16) + 64 bytes metadata overhead.
     */
    public int estimatedBytes() {
        int textBytes = (oldText.length() + newText.length()) * 2; //mỗi 1 ký tự (char) trong Java luôn tốn đúng 2 bytes 
        int metadataBytes = 64; //"ước chừng" cho các thành phần ẩn (Object Header, con trỏ, padding...) do máy ảo JVM bí mật nhét vào
        return textBytes + metadataBytes;
    }

    /** Human-readable description for the history viewer. */
    public String getDescription() {
        String time = String.format("%tT", timestamp); //ép thành định dạng giờ chuẩn quốc tế HH:MM:SS
        switch (type) {
            case "INSERT":
                return String.format("[%s] INSERT \"%s\" at %d", time, truncate(newText, 30), position);
            case "DELETE":
                return String.format("[%s] DELETE \"%s\" at %d", time, truncate(oldText, 30), position);
            case "REPLACE":
                return String.format("[%s] REPLACE \"%s\" -> \"%s\" at %d",
                        time, truncate(oldText, 20), truncate(newText, 20), position);
            default:
                return String.format("[%s] %s at %d", time, type, position);//trường hợp dự phòng loại lệnh lạ
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    // ── Getters & Setters ─────────────────────────────────────────

    public String getType() { return type; }
    public int getPosition() { return position; }
    public String getOldText() { return oldText; }
    public String getNewText() { return newText; }
    public long getTimestamp() { return timestamp; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}

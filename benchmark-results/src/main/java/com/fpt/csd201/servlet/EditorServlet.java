package com.fpt.csd201.servlet;

import com.fpt.csd201.editor.*;
import com.fpt.csd201.history.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * EditorServlet — Connects web UI buttons to Java undo/redo logic.
 *
 * Handles actions: insert, delete, replace, undo, redo, save, load,
 * search, getState, undoTo, switchModel.
 *
 * Editor state (TextEditor + UndoRedoManager) is stored in HttpSession
 * so each user session keeps its own history.
 */
@WebServlet(urlPatterns = "/api/editor")
public class EditorServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String SESSION_EDITOR = "textEditor";
    private static final String SESSION_MANAGER = "undoRedoManager";
    private static final String SESSION_SAVED = "savedContent";
    private static final String SESSION_LAST_GROUP_ID = "lastGroupId";

    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        // Read JSON body
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        JsonObject json;
        try {
            json = JsonParser.parseString(sb.toString()).getAsJsonObject();
        } catch (Exception e) {
            sendError(response, "Invalid JSON request body.");
            return;
        }

        String action = json.has("action") ? json.get("action").getAsString() : "";
        HttpSession session = request.getSession(true);

        // Initialize session state if needed
        TextEditor editor = (TextEditor) session.getAttribute(SESSION_EDITOR);
        UndoRedoManager manager = (UndoRedoManager) session.getAttribute(SESSION_MANAGER);
        if (editor == null) {
            editor = new TextEditor();
            session.setAttribute(SESSION_EDITOR, editor);
        }
        if (manager == null) {
            manager = new UndoRedoManager(new CommandHistory());
            session.setAttribute(SESSION_MANAGER, manager);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);

        try {
            switch (action) {
                case "insert":
                    handleInsert(json, editor, manager, session, result);
                    break;
                case "delete":
                    handleDelete(json, editor, manager, result);
                    break;
                case "replace":
                    handleReplace(json, editor, manager, result);
                    break;
                case "undo":
                    handleUndo(editor, manager, result);
                    break;
                case "redo":
                    handleRedo(editor, manager, result);
                    break;
                case "save":
                    handleSave(editor, session, result);
                    break;
                case "load":
                    handleLoad(editor, manager, session, result);
                    break;
                case "search":
                    handleSearch(json, editor, result);
                    break;
                case "undoTo":
                    handleUndoTo(json, editor, manager, result);
                    break;
                case "switchModel":
                    handleSwitchModel(json, editor, manager, session, result);
                    break;
                case "getState":
                    // Just return current state (handled below)
                    result.put("message", "");
                    break;
                default:
                    result.put("success", false);
                    result.put("message", "Unknown action: " + action);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Error: " + e.getMessage());
        }

        // Always attach current state
        result.put("content", editor.getContent());
        result.put("canUndo", manager.canUndo());
        result.put("canRedo", manager.canRedo());
        result.put("modelName", manager.getHistoryModel().getModelName());
        result.put("memoryUsage", manager.getMemoryUsage());
        result.put("undoCount", manager.getHistoryModel().getUndoCount());
        result.put("redoCount", manager.getHistoryModel().getRedoCount());

        // Build action history for the viewer
        List<Map<String, Object>> historyList = new ArrayList<>();
        List<Operation> history = manager.getActionHistory();
        for (int i = 0; i < history.size(); i++) {
            Operation op = history.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", i);
            entry.put("description", op.getDescription());
            entry.put("type", op.getType());
            entry.put("groupId", op.getGroupId());
            entry.put("active", true);
            historyList.add(entry);
        }
        
        List<Operation> redoHistory = manager.getRedoActionHistory();
        for (int i = redoHistory.size() - 1; i >= 0; i--) {
            Operation op = redoHistory.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("index", history.size() + (redoHistory.size() - 1 - i));
            entry.put("description", op.getDescription());
            entry.put("type", op.getType());
            entry.put("groupId", op.getGroupId());
            entry.put("active", false);
            historyList.add(entry);
        }
        result.put("history", historyList);

        // Send response
        try (PrintWriter out = response.getWriter()) {
            out.print(gson.toJson(result));
        }
    }

    // ── Action Handlers ───────────────────────────────────────────

    private void handleInsert(JsonObject json, TextEditor editor,
                              UndoRedoManager manager, HttpSession session,
                              Map<String, Object> result) {
        int position = json.has("position") ? json.get("position").getAsInt() : editor.getContent().length();
        String text = json.has("text") ? json.get("text").getAsString() : "";
        if (text.isEmpty()) {
            result.put("message", "No text to insert.");
            return;
        }

        // ── Group ID logic (only for INSERT) ──
        String groupId = resolveGroupId(json, manager, session, position, text);

        InsertOperation op = new InsertOperation(position, text, groupId);
        manager.recordOperation(op, editor);
        result.put("message", "Inserted \"" + truncate(text, 30) + "\" at " + position);
    }

    /**
     * Resolve groupId for an InsertOperation.
     *
     * Group only if ALL conditions are met:
     *   1. Previous operation exists
     *   2. Previous operation type is INSERT
     *   3. Current operation type is INSERT (always true here)
     *   4. Time difference <= 800ms
     *   5. Current position === previous.position + previous.newText.length
     *      (cursor is adjacent — did not jump)
     */
    private String resolveGroupId(JsonObject json, UndoRedoManager manager,
                                   HttpSession session, int position, String text) {
        // Check if this is from typing (auto-detected change) or button click
        boolean fromTyping = json.has("fromTyping") && json.get("fromTyping").getAsBoolean();
        if (!fromTyping) {
            // Button-triggered inserts start a new group
            return null;
        }

        Operation prev = manager.getHistoryModel().peekUndo();
        if (prev == null) {
            // No previous operation — start new group
            String newGroupId = UUID.randomUUID().toString();
            session.setAttribute(SESSION_LAST_GROUP_ID, newGroupId);
            return newGroupId;
        }

        if (!"INSERT".equals(prev.getType())) {
            // Previous was not INSERT — start new group
            String newGroupId = UUID.randomUUID().toString();
            session.setAttribute(SESSION_LAST_GROUP_ID, newGroupId);
            return newGroupId;
        }

        long timeDiff = System.currentTimeMillis() - prev.getTimestamp();
        if (timeDiff > 800) {
            // Time gap too large — start new group
            String newGroupId = UUID.randomUUID().toString();
            session.setAttribute(SESSION_LAST_GROUP_ID, newGroupId);
            return newGroupId;
        }

        int expectedPosition = prev.getPosition() + prev.getNewText().length();
        if (position != expectedPosition) {
            // Cursor jumped — start new group
            String newGroupId = UUID.randomUUID().toString();
            session.setAttribute(SESSION_LAST_GROUP_ID, newGroupId);
            return newGroupId;
        }

        // All conditions met — continue the existing group
        String existingGroupId = prev.getGroupId();
        if (existingGroupId == null) {
            // Previous INSERT had no group — create one and retroactively assign
            String newGroupId = UUID.randomUUID().toString();
            prev.setGroupId(newGroupId);
            session.setAttribute(SESSION_LAST_GROUP_ID, newGroupId);
            return newGroupId;
        }
        return existingGroupId;
    }

    private void handleDelete(JsonObject json, TextEditor editor,
                               UndoRedoManager manager, Map<String, Object> result) {
        int position = json.has("position") ? json.get("position").getAsInt() : 0;
        int length = json.has("length") ? json.get("length").getAsInt() : 0;

        if (length <= 0 || position < 0 || position >= editor.getContent().length()) {
            result.put("message", "Invalid delete parameters.");
            return;
        }

        int safeLength = Math.min(length, editor.getContent().length() - position);
        String oldText = editor.getContent().substring(position, position + safeLength);

        // Delete operations are NEVER grouped
        DeleteOperation op = new DeleteOperation(position, oldText);
        manager.recordOperation(op, editor);
        result.put("message", "Deleted \"" + truncate(oldText, 30) + "\" at " + position);
    }

    private void handleReplace(JsonObject json, TextEditor editor,
                                UndoRedoManager manager, Map<String, Object> result) {
        int position = json.has("position") ? json.get("position").getAsInt() : 0;
        int length = json.has("length") ? json.get("length").getAsInt() : 0;
        String newText = json.has("newText") ? json.get("newText").getAsString() : "";

        if (length <= 0 || position < 0 || position >= editor.getContent().length()) {
            result.put("message", "Invalid replace parameters.");
            return;
        }

        int safeLength = Math.min(length, editor.getContent().length() - position);
        String oldText = editor.getContent().substring(position, position + safeLength);

        // Replace operations are NEVER grouped
        ReplaceOperation op = new ReplaceOperation(position, oldText, newText);
        manager.recordOperation(op, editor);
        result.put("message", "Replaced \"" + truncate(oldText, 20) + "\" with \""
                + truncate(newText, 20) + "\" at " + position);
    }

    private void handleUndo(TextEditor editor, UndoRedoManager manager,
                             Map<String, Object> result) {
        if (!manager.canUndo()) {
            result.put("message", "Nothing to undo");
            return;
        }
        manager.undo(editor);
        result.put("message", "Undo successful");
    }

    private void handleRedo(TextEditor editor, UndoRedoManager manager,
                             Map<String, Object> result) {
        if (!manager.canRedo()) {
            result.put("message", "Nothing to redo");
            return;
        }
        manager.redo(editor);
        result.put("message", "Redo successful");
    }

    private void handleSave(TextEditor editor, HttpSession session,
                             Map<String, Object> result) {
        session.setAttribute(SESSION_SAVED, editor.getContent());
        result.put("message", "Document saved");
    }

    private void handleLoad(TextEditor editor, UndoRedoManager manager,
                             HttpSession session, Map<String, Object> result) {
        String saved = (String) session.getAttribute(SESSION_SAVED);
        if (saved == null) {
            result.put("message", "No saved document found.");
            return;
        }
        editor.setContent(saved);
        // Reset undo/redo history on load
        manager.setHistoryModel(createFreshModel(manager.getHistoryModel().getModelName()));
        result.put("message", "Document loaded");
    }

    private void handleSearch(JsonObject json, TextEditor editor,
                               Map<String, Object> result) {
        String keyword = json.has("keyword") ? json.get("keyword").getAsString() : "";
        if (keyword.isEmpty()) {
            result.put("message", "No search keyword provided.");
            result.put("searchResults", new ArrayList<>());
            return;
        }

        String content = editor.getContent();
        List<Map<String, Integer>> matches = new ArrayList<>();
        int idx = 0;
        String lowerContent = content.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        while ((idx = lowerContent.indexOf(lowerKeyword, idx)) != -1) {
            Map<String, Integer> match = new LinkedHashMap<>();
            match.put("start", idx);
            match.put("end", idx + keyword.length());
            matches.add(match);
            idx += 1;
        }
        result.put("searchResults", matches);
        result.put("message", matches.size() + " match(es) found for \"" + keyword + "\"");
    }

    private void handleUndoTo(JsonObject json, TextEditor editor,
                               UndoRedoManager manager, Map<String, Object> result) {
        int targetIndex = json.has("targetIndex") ? json.get("targetIndex").getAsInt() : -1;
        if (targetIndex < -1) {
            result.put("message", "Invalid target index.");
            return;
        }
        manager.undoTo(targetIndex, editor);
        result.put("message", "Reverted to action #" + (targetIndex + 1));
    }

    private void handleSwitchModel(JsonObject json, TextEditor editor,
                                    UndoRedoManager manager, HttpSession session,
                                    Map<String, Object> result) {
        String modelName = json.has("model") ? json.get("model").getAsString() : "CommandHistory";
        HistoryStrategy newModel = createFreshModel(modelName);
        manager.setHistoryModel(newModel);
        result.put("message", "Switched to " + newModel.getModelName() + ". History reset.");
    }

    /** Create a fresh (empty) history model by name. */
    private HistoryStrategy createFreshModel(String name) {
        switch (name) {
            case "SnapshotHistory":
                return new SnapshotHistory();
            case "TwoStackHistory":
                return new TwoStackHistory();
            case "DequeHistory":
                return new DequeHistory();
            default:
                return new CommandHistory();
        }
    }

    // ── Utility ───────────────────────────────────────────────────

    private void sendError(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("message", message);
        try (PrintWriter out = response.getWriter()) {
            out.print(new Gson().toJson(err));
        }
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }
}

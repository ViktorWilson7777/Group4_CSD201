package com.fpt.csd201.console;

import com.fpt.csd201.editor.*;
import com.fpt.csd201.history.*;
import com.fpt.csd201.benchmark.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ConsoleApp {
    private TextEditor editor;
    private UndoRedoManager manager;
    private Scanner scanner;
    private String currentModelName;

    public static void main(String[] args) {
        new ConsoleApp().run();
    }

    public ConsoleApp() {
        editor = new TextEditor();
        manager = new UndoRedoManager(new CommandHistory());
        scanner = new Scanner(System.in);
        currentModelName = "CommandHistory (RQ2)";
    }

    private void run() {
        while (true) {
            clearScreen();
            printDashboard();
            
            System.out.print(">> Choose [0-12]: ");
            int choice = readInt();
            
            switch (choice) {
                case 0:
                    System.out.println("Goodbye!");
                    return;
                case 1: handleInsert(); break;
                case 2: handleDelete(); break;
                case 3: handleReplace(); break;
                case 4: handleViewFull(); break;
                case 5: handleSearch(); break;
                case 6: handleUndo(); break;
                case 7: handleRedo(); break;
                case 8: handleViewHistory(); break;
                case 9: handleSwitchModel(); break;
                case 10: handleBenchmark(); break;
                case 11: handleSave(); break;
                case 12: handleLoad(); break;
                default:
                    System.out.println("Invalid choice!");
            }
            
            System.out.println("\nPress Enter to continue...");
            scanner.nextLine(); // wait for user
        }
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    private void printDashboard() {
        System.out.println("+---------------------------------------------------------------------------------------------------+");
        System.out.println("|                             CSD201 - UNDO/REDO TEXT EDITOR (Console)                              |");
        System.out.println("+--------------------------------------------+------------------------------------------------------+");
        System.out.println("|  CONTENT                                   |  ACTION HISTORY                                      |");
        System.out.println("+--------------------------------------------+------------------------------------------------------+");
        
        String[] contentLines = editor.getContent().isEmpty() ? new String[]{"(Empty)"} : editor.getContent().replace("\r", "").split("\n");
        
        // Prepare right column lines (Undo and Redo)
        List<String> rightLines = new ArrayList<>();
        List<Operation> undoList = manager.getActionHistory();
        List<Operation> redoList = manager.getRedoActionHistory();
        
        rightLines.add("--- UNDO STACK (" + manager.getHistoryModel().getUndoCount() + ") ---");
        int uStart = Math.max(0, undoList.size() - 5);
        if (undoList.isEmpty()) { rightLines.add("  (Empty)"); }
        for (int i = uStart; i < undoList.size(); i++) {
            String desc = undoList.get(i).getDescription().replace("\n", "\\n").replace("\r", "");
            rightLines.add("  " + (i+1) + ". " + desc);
        }
        
        rightLines.add("--- REDO STACK (" + manager.getHistoryModel().getRedoCount() + ") ---");
        int rStart = Math.max(0, redoList.size() - 5);
        if (redoList.isEmpty()) { rightLines.add("  (Empty)"); }
        for (int i = rStart; i < redoList.size(); i++) {
            String desc = redoList.get(i).getDescription().replace("\n", "\\n").replace("\r", "");
            rightLines.add("  " + (i+1) + ". " + desc);
        }
        
        int maxRows = Math.max(contentLines.length, rightLines.size());
        maxRows = Math.max(maxRows, 5); // display at least 5 rows
        
        for (int i = 0; i < maxRows; i++) {
            String left = "";
            if (i < contentLines.length) {
                left = contentLines[i].replace("\t", "    ");
                if (left.length() > 40) left = left.substring(0, 37) + "...";
            }
            
            String right = "";
            if (i < rightLines.size()) {
                right = rightLines.get(i).replace("\t", "    ");
                if (right.length() > 50) right = right.substring(0, 47) + "...";
            }
            System.out.printf("|  %-40s  |  %-50s  |\n", left, right);
        }
        
        System.out.println("+--------------------------------------------+------------------------------------------------------+");
        String status = String.format("  Model: %s | Undo: %d | Redo: %d | Mem: %s", 
                currentModelName, manager.getHistoryModel().getUndoCount(), manager.getHistoryModel().getRedoCount(), formatBytes(manager.getMemoryUsage()));
        System.out.printf("|%-97s  |\n", status);
        System.out.println("+---------------------------------------------------------------------------------------------------+");
        System.out.println("|  1. Insert    4. View full   7. Redo       10. Benchmark                                      |");
        System.out.println("|  2. Delete    5. Search      8. History    11. Save file                                      |");
        System.out.println("|  3. Replace   6. Undo        9. Switch     12. Load file                               0.Exit |");
        System.out.println("+---------------------------------------------------------------------------------------------------+");
    }

    private void handleInsert() {
        System.out.print(">> Enter insert position (0 = start): ");
        int pos = readInt();
        System.out.println(">> Enter text to insert (type \\n for newline, \\\\n for literal \\n): ");
        String text = parseInput(scanner.nextLine());
        
        InsertOperation op = new InsertOperation(pos, text, null);
        manager.recordOperation(op, editor);
        System.out.println("✓ Text inserted successfully.");
    }

    private void handleDelete() {
        System.out.print(">> Enter delete start position: ");
        int pos = readInt();
        System.out.print(">> Enter number of characters to delete: ");
        int len = readInt();
        
        String content = editor.getContent();
        pos = Math.max(0, Math.min(pos, content.length()));
        len = Math.min(len, content.length() - pos);
        
        if (len == 0) {
            System.out.println("✗ Nothing to delete at this position.");
            return;
        }
        
        String deletedText = content.substring(pos, pos + len);
        DeleteOperation op = new DeleteOperation(pos, deletedText);
        manager.recordOperation(op, editor);
        System.out.println("✓ Deleted " + len + " characters.");
    }

    private void handleReplace() {
        System.out.print(">> Enter replace start position: ");
        int pos = readInt();
        System.out.print(">> Enter number of characters to replace: ");
        int len = readInt();
        
        String content = editor.getContent();
        pos = Math.max(0, Math.min(pos, content.length()));
        len = Math.min(len, content.length() - pos);
        
        String oldText = "";
        if (len > 0) {
            oldText = content.substring(pos, pos + len);
        }
        
        System.out.print(">> Enter new text (type \\n for newline, \\\\n for literal \\n): ");
        String newText = parseInput(scanner.nextLine());
        
        ReplaceOperation op = new ReplaceOperation(pos, oldText, newText);
        manager.recordOperation(op, editor);
        System.out.println("✓ Replaced \"" + oldText.replace("\n", "\\n") + "\" with new text.");
    }

    private void handleViewFull() {
        System.out.println("\n--- Document Content ---");
        String content = editor.getContent();
        if (content.isEmpty()) {
            System.out.println("(Empty)");
        } else {
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                System.out.printf("%3d | %s\n", i + 1, lines[i]);
            }
        }
        System.out.println("--- End (" + content.length() + " characters) ---");
    }

    private void handleSearch() {
        System.out.print(">> Enter search keyword: ");
        String keyword = scanner.nextLine();
        if (keyword.isEmpty()) return;
        
        String content = editor.getContent();
        String lowerContent = content.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        
        int idx = 0;
        int count = 0;
        System.out.println("Search results:");
        while ((idx = lowerContent.indexOf(lowerKeyword, idx)) != -1) {
            count++;
            int startContext = Math.max(0, idx - 15);
            int endContext = Math.min(content.length(), idx + keyword.length() + 15);
            String context = content.substring(startContext, idx) + ">>>" + content.substring(idx, idx + keyword.length()) + "<<<" + content.substring(idx + keyword.length(), endContext);
            System.out.printf("  [%d] Position %d: \"...%s...\"\n", count, idx, context.replace("\n", " "));
            idx += keyword.length();
        }
        if (count == 0) {
            System.out.println("✗ No matches found.");
        } else {
            System.out.println("✓ Found " + count + " matches.");
        }
    }

    private void handleUndo() {
        if (manager.undo(editor)) {
            System.out.println("✓ Undo successful!");
        } else {
            System.out.println("✗ Nothing to Undo.");
        }
    }

    private void handleRedo() {
        if (manager.redo(editor)) {
            System.out.println("✓ Redo successful!");
        } else {
            System.out.println("✗ Nothing to Redo.");
        }
    }

    private void handleViewHistory() {
        System.out.println("\n=== UNDO STACK (" + manager.getHistoryModel().getUndoCount() + " actions) ===");
        List<Operation> undoList = manager.getActionHistory();
        if (undoList.isEmpty()) {
            System.out.println("  (Empty)");
        } else {
            for (int i = 0; i < undoList.size(); i++) {
                Operation op = undoList.get(i);
                System.out.printf(" %3d | %-8s | %s\n", i + 1, op.getType(), op.getDescription().replace("\n", "\\n"));
            }
        }
        
        System.out.println("\n=== REDO STACK (" + manager.getHistoryModel().getRedoCount() + " actions) ===");
        List<Operation> redoList = manager.getRedoActionHistory();
        if (redoList == null || redoList.isEmpty()) {
            System.out.println("  (Empty)");
        } else {
            for (int i = 0; i < redoList.size(); i++) {
                Operation op = redoList.get(i);
                System.out.printf(" %3d | %-8s | %s\n", i + 1, op.getType(), op.getDescription().replace("\n", "\\n"));
            }
        }
    }

    private void handleSwitchModel() {
        System.out.println("\nSelect History Model:");
        System.out.println("  1. CommandHistory      (RQ2)");
        System.out.println("  2. SnapshotHistory     (RQ2)");
        System.out.println("  3. BoundedStackHistory (RQ1)");
        System.out.println("  4. LRUStackHistory     (RQ1)");
        System.out.println("  5. TwoStackHistory     (RQ3)");
        System.out.println("  6. DequeHistory        (RQ3)");
        System.out.print(">> Choose [1-6]: ");
        int choice = readInt();
        
        HistoryStrategy newModel;
        String newName;
        switch (choice) {
            case 1: newModel = new CommandHistory(); newName = "CommandHistory (RQ2)"; break;
            case 2: newModel = new SnapshotHistory(); newName = "SnapshotHistory (RQ2)"; break;
            case 3: newModel = new BoundedStackHistory(); newName = "BoundedStackHistory (RQ1)"; break;
            case 4: newModel = new LRUStackHistory(); newName = "LRUStackHistory (RQ1)"; break;
            case 5: newModel = new TwoStackHistory(); newName = "TwoStackHistory (RQ3)"; break;
            case 6: newModel = new DequeHistory(); newName = "DequeHistory (RQ3)"; break;
            default: System.out.println("✗ Invalid choice."); return;
        }
        
        manager.setHistoryModel(newModel);
        editor.setContent("");
        currentModelName = newName;
        System.out.println("✓ Switched to " + newName + " and reset content.");
    }

    private void handleBenchmark() {
        System.out.println("\nSelect Research Question for Benchmark:");
        System.out.println("  1. RQ1: BoundedStack vs LRUStack (Memory limits)");
        System.out.println("  2. RQ2: CommandHistory vs SnapshotHistory (10MB text)");
        System.out.println("  3. RQ3: TwoStack vs Deque (Timing performance)");
        System.out.print(">> Choose [1-3]: ");
        int choice = readInt();
        
        BenchmarkRunner runner = new BenchmarkRunner();
        List<BenchmarkRunner.BenchmarkResult> results = null;
        
        System.out.println("Running benchmark... Please wait.");
        try {
            switch (choice) {
                case 1: results = runner.runRQ1(); break;
                case 2: results = runner.runRQ2(); break;
                case 3: results = runner.runRQ3(); break;
                default: System.out.println("✗ Invalid choice."); return;
            }
        } catch (Exception e) {
            System.out.println("✗ Benchmark error: " + e.getMessage());
            return;
        }
        
        if (results == null || results.isEmpty()) {
            System.out.println("✗ No results.");
            return;
        }
        
        System.out.println("\n=== BENCHMARK RESULTS RQ" + choice + " ===");
        for (BenchmarkRunner.BenchmarkResult r : results) {
            Map<String, Object> map = r.toMap();
            System.out.println("--------------------------------------------------");
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                System.out.printf("%-20s: %s\n", entry.getKey(), entry.getValue());
            }
        }
        System.out.println("--------------------------------------------------");
    }

    private void handleSave() {
        System.out.print(">> Enter absolute file path or filename to save (e.g. output.txt): ");
        String path = scanner.nextLine();
        if (path.isEmpty()) return;
        
        try (FileWriter writer = new FileWriter(path)) {
            writer.write(editor.getContent());
            System.out.println("✓ Saved content to: " + new File(path).getAbsolutePath());
        } catch (IOException e) {
            System.out.println("✗ Error saving file: " + e.getMessage());
        }
    }

    private void handleLoad() {
        System.out.print(">> Enter absolute file path or filename to load (e.g. input.txt): ");
        String path = scanner.nextLine();
        if (path.isEmpty()) return;
        
        try {
            String content = new String(Files.readAllBytes(Paths.get(path)));
            editor.setContent(content);
            manager.setHistoryModel(new CommandHistory()); // Reset history
            currentModelName = "CommandHistory (RQ2)";
            System.out.println("✓ Loaded content from: " + new File(path).getAbsolutePath());
        } catch (IOException e) {
            System.out.println("✗ Error loading file: " + e.getMessage());
        }
    }

    private int readInt() {
        while (true) {
            try {
                int value = scanner.nextInt();
                scanner.nextLine(); // consume newline
                return value;
            } catch (InputMismatchException e) {
                System.out.print("✗ Please enter an integer: ");
                scanner.nextLine(); // clear bad input
            }
        }
    }

    private String formatBytes(long bytes) {
        if (bytes == 0) return "0 B";
        String[] units = {"B", "KB", "MB", "GB"};
        int i = (int) Math.floor(Math.log(bytes) / Math.log(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, i), units[i]);
    }

    private String parseInput(String input) {
        if (input == null) return "";
        return input.replaceAll("(?<!\\\\)\\\\n", "\n").replace("\\\\n", "\\n");
    }
}

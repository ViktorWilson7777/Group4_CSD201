package com.fpt.csd201.benchmark;

import com.fpt.csd201.editor.*;
import com.fpt.csd201.history.*;

import java.io.Serializable;
import java.util.*;

/**
 * BenchmarkRunner — Runs benchmarks for the 3 research questions.
 *
 * RQ1: BoundedStackHistory (count-limited) vs LRUStackHistory (memory-limited)
 * RQ2: CommandHistory (operations) vs SnapshotHistory (full snapshots)
 * RQ3: TwoStackHistory vs DequeHistory (undo/redo timing)
 *
 * Fairness rule: one deterministic operation list is generated and reused
 * for every model in the same benchmark.
 *
 * Memory rule: uses estimatedBytes() for internal memory estimation.
 */
public class BenchmarkRunner implements Serializable {
    private static final long serialVersionUID = 1L;

    // ── Result Data Structures ────────────────────────────────────

    public static class BenchmarkResult implements Serializable {
        private static final long serialVersionUID = 1L;
        public String modelName;
        public int operationCount;
        public long memoryUsageBytes;
        public int undoLevelsRetained;
        public int evictedCount;
        public double avgUndoTimeMs;
        public double avgRedoTimeMs;
        public double totalTimeMs;
        public boolean underMemoryLimit;
        public String notes;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("modelName", modelName);
            map.put("operationCount", operationCount);
            map.put("memoryUsageBytes", memoryUsageBytes);
            map.put("memoryUsageMB", String.format("%.2f", memoryUsageBytes / (1024.0 * 1024.0)));
            map.put("undoLevelsRetained", undoLevelsRetained);
            map.put("evictedCount", evictedCount);
            map.put("avgUndoTimeMs", String.format("%.4f", avgUndoTimeMs));
            map.put("avgRedoTimeMs", String.format("%.4f", avgRedoTimeMs));
            map.put("totalTimeMs", String.format("%.2f", totalTimeMs));
            map.put("underMemoryLimit", underMemoryLimit);
            map.put("notes", notes);
            return map;
        }
    }

    // ── Operation Generation ──────────────────────────────────────

    /**
     * Generate a deterministic list of InsertOperation objects.
     * Uses a seeded Random so results are reproducible.
     * Each operation inserts a small random string at a valid position.
     */
    public List<Operation> generateOperations(int count) {
        Random random = new Random(42); // fixed seed for reproducibility
        List<Operation> ops = new ArrayList<>();
        int currentLength = 0;

        for (int i = 0; i < count; i++) {
            // Generate a random insert string (1-10 chars)
            int strLen = 1 + random.nextInt(10);
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < strLen; j++) {
                sb.append((char) ('a' + random.nextInt(26)));
            }
            String text = sb.toString();

            // Insert at a valid position
            int position = currentLength == 0 ? 0 : random.nextInt(currentLength + 1);
            InsertOperation op = new InsertOperation(position, text, null);
            ops.add(op);
            currentLength += text.length();
        }
        return ops;
    }

    /**
     * Generate a large text string of approximately sizeMB megabytes.
     * Uses repeated lorem-ipsum-style sentences.
     */
    public String generateLargeText(int sizeMB) {
        long targetBytes = (long) sizeMB * 1024 * 1024;
        // Each Java char is 2 bytes in memory; we target targetBytes/2 chars
        long targetChars = targetBytes / 2;

        String[] sentences = {
            "The quick brown fox jumps over the lazy dog. ",
            "Data structures are fundamental to computer science. ",
            "Stacks follow Last-In-First-Out ordering for elements. ",
            "The Command Pattern encapsulates operations as objects. ",
            "Undo and Redo are essential features in modern editors. ",
            "A deque allows insertion and removal at both ends. ",
            "Memory management is critical for large-scale applications. ",
            "Binary trees enable efficient searching and sorting. ",
            "Hash tables provide constant-time average-case lookups. ",
            "Linked lists allow dynamic memory allocation at runtime. "
        };

        StringBuilder sb = new StringBuilder((int) Math.min(targetChars, Integer.MAX_VALUE - 8));
        int idx = 0;
        while (sb.length() < targetChars) {
            sb.append(sentences[idx % sentences.length]);
            idx++;
        }
        return sb.toString();
    }

    // ── RQ1: BoundedStack vs LRUStack ─────────────────────────────

    /**
     * RQ1: Compare BoundedStackHistory (fixed limit N) with LRUStackHistory
     * (memory eviction) when users perform more than 500 consecutive operations.
     *
     * Tests at 500, 1000, and 2000 operation counts.
     */
    public List<BenchmarkResult> runRQ1() {
        int[] counts = {500, 1000, 2000};
        List<BenchmarkResult> results = new ArrayList<>();

        for (int count : counts) {
            List<Operation> ops = generateOperations(count);

            // ── BoundedStackHistory (limit=500) ──
            {
                BoundedStackHistory model = new BoundedStackHistory(500);
                TextEditor editor = new TextEditor();
                for (Operation op : ops) {
                    // Clone operation to avoid shared state
                    InsertOperation clone = new InsertOperation(op.getPosition(), op.getNewText(), null);
                    clone.setTimestamp(op.getTimestamp());
                    model.record(clone, editor);
                }

                BenchmarkResult r = new BenchmarkResult();
                r.modelName = model.getModelName();
                r.operationCount = count;
                r.memoryUsageBytes = model.getMemoryUsage();
                r.undoLevelsRetained = model.getUndoCount();
                r.evictedCount = model.getEvictedCount();
                r.underMemoryLimit = r.memoryUsageBytes < 50L * 1024 * 1024;
                r.notes = "Fixed count limit = 500. Evicts oldest when count > 500.";
                results.add(r);
            }

            // ── LRUStackHistory (maxBytes=50MB) ──
            {
                LRUStackHistory model = new LRUStackHistory(50L * 1024 * 1024);
                TextEditor editor = new TextEditor();
                for (Operation op : ops) {
                    InsertOperation clone = new InsertOperation(op.getPosition(), op.getNewText(), null);
                    clone.setTimestamp(op.getTimestamp());
                    model.record(clone, editor);
                }

                BenchmarkResult r = new BenchmarkResult();
                r.modelName = model.getModelName();
                r.operationCount = count;
                r.memoryUsageBytes = model.getMemoryUsage();
                r.undoLevelsRetained = model.getUndoCount();
                r.evictedCount = model.getEvictedCount();
                r.underMemoryLimit = r.memoryUsageBytes < 50L * 1024 * 1024;
                r.notes = "Memory limit = 50MB. Evicts oldest when bytes exceed threshold.";
                results.add(r);
            }
        }
        return results;
    }

    // ── RQ2: CommandHistory vs SnapshotHistory ────────────────────

    /**
     * RQ2: Compare CommandHistory and SnapshotHistory on a 10MB text file.
     * Check whether Command Pattern + Stack reduces stored data enough
     * to keep memory usage under 50MB.
     */
    public List<BenchmarkResult> runRQ2() {
        List<BenchmarkResult> results = new ArrayList<>();
        int opCount = 100;
        List<Operation> ops = generateOperations(opCount);

        // Generate 10MB initial text
        String largeText = generateLargeText(10);

        // ── CommandHistory ──
        {
            CommandHistory model = new CommandHistory();
            TextEditor editor = new TextEditor();
            editor.setContent(largeText);

            for (Operation op : ops) {
                // Adjust position to be within the current content length
                int pos = Math.min(op.getPosition(), editor.getContent().length());
                InsertOperation clone = new InsertOperation(pos, op.getNewText(), null);
                clone.setTimestamp(op.getTimestamp());
                model.record(clone, editor);
            }

            BenchmarkResult r = new BenchmarkResult();
            r.modelName = model.getModelName();
            r.operationCount = opCount;
            r.memoryUsageBytes = model.getMemoryUsage();
            r.undoLevelsRetained = model.getUndoCount();
            r.underMemoryLimit = r.memoryUsageBytes < 50L * 1024 * 1024;
            r.notes = "Stores Operation objects (type, position, text fragments). "
                    + "Initial text: 10MB. Memory = sum of estimatedBytes() for each operation.";
            results.add(r);
        }

        // ── SnapshotHistory ──
        {
            SnapshotHistory model = new SnapshotHistory();
            TextEditor editor = new TextEditor();
            editor.setContent(largeText);

            for (Operation op : ops) {
                int pos = Math.min(op.getPosition(), editor.getContent().length());
                InsertOperation clone = new InsertOperation(pos, op.getNewText(), null);
                clone.setTimestamp(op.getTimestamp());
                model.record(clone, editor);
            }

            BenchmarkResult r = new BenchmarkResult();
            r.modelName = model.getModelName();
            r.operationCount = opCount;
            r.memoryUsageBytes = model.getMemoryUsage();
            r.undoLevelsRetained = model.getUndoCount();
            r.underMemoryLimit = r.memoryUsageBytes < 50L * 1024 * 1024;
            r.notes = "Stores FULL editor content snapshot after every operation. "
                    + "Initial text: 10MB. Memory = sum of all snapshot lengths * 2.";
            results.add(r);
        }

        return results;
    }

    // ── RQ3: TwoStackHistory vs DequeHistory (timing) ─────────────

    /**
     * RQ3: Compare average Undo and Redo time between TwoStackHistory
     * and DequeHistory. Uses performance timing (System.nanoTime).
     *
     * Runs at least 1000 undo and redo operations on each model.
     */
    public List<BenchmarkResult> runRQ3() {
        List<BenchmarkResult> results = new ArrayList<>();
        int opCount = 1000;
        List<Operation> ops = generateOperations(opCount);

        // ── TwoStackHistory ──
        results.add(benchmarkTimingModel(new TwoStackHistory(), ops, opCount));

        // ── DequeHistory ──
        results.add(benchmarkTimingModel(new DequeHistory(), ops, opCount));

        return results;
    }

    /**
     * Benchmark undo/redo timing for a given history model.
     */
    private BenchmarkResult benchmarkTimingModel(HistoryStrategy model, List<Operation> ops, int opCount) {
        TextEditor editor = new TextEditor();

        // Record all operations
        for (Operation op : ops) {
            int pos = Math.min(op.getPosition(), editor.getContent().length());
            InsertOperation clone = new InsertOperation(pos, op.getNewText(), null);
            clone.setTimestamp(op.getTimestamp());
            model.record(clone, editor);
        }

        // ── Measure UNDO time ──
        long undoStart = System.nanoTime();
        int undoCount = 0;
        while (model.undo(editor)) {
            undoCount++;
        }
        long undoEnd = System.nanoTime();
        double totalUndoMs = (undoEnd - undoStart) / 1_000_000.0;
        double avgUndoMs = undoCount > 0 ? totalUndoMs / undoCount : 0;

        // ── Measure REDO time ──
        long redoStart = System.nanoTime();
        int redoCount = 0;
        while (model.redo(editor)) {
            redoCount++;
        }
        long redoEnd = System.nanoTime();
        double totalRedoMs = (redoEnd - redoStart) / 1_000_000.0;
        double avgRedoMs = redoCount > 0 ? totalRedoMs / redoCount : 0;

        BenchmarkResult r = new BenchmarkResult();
        r.modelName = model.getModelName();
        r.operationCount = opCount;
        r.memoryUsageBytes = model.getMemoryUsage();
        r.undoLevelsRetained = model.getUndoCount();
        r.avgUndoTimeMs = avgUndoMs;
        r.avgRedoTimeMs = avgRedoMs;
        r.totalTimeMs = totalUndoMs + totalRedoMs;
        r.notes = String.format("Undo ops: %d, Redo ops: %d. Timing via System.nanoTime().",
                undoCount, redoCount);
        return r;
    }
}

# TEST CASES — CSD201 Undo/Redo Text Editor

This document lists the required test cases for validating the Undo/Redo system.

---

## TC-01: Insert Text → Undo → Redo

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open the app. Editor is empty. | Textarea shows `""` |
| 2 | Click **Insert** button. Enter position `0`, text `"hello"`. | Editor shows `"hello"` |
| 3 | Click **Undo**. | Editor shows `""` (insert reversed) |
| 4 | Click **Redo**. | Editor shows `"hello"` (insert reapplied) |
| 5 | History viewer shows 1 INSERT operation. | ✓ |

---

## TC-02: Delete Text → Undo → Redo

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert `"hello world"` at position 0. | Editor shows `"hello world"` |
| 2 | Click **Delete**. Enter position `5`, length `6`. | Editor shows `"hello"` (deleted `" world"`) |
| 3 | Click **Undo**. | Editor shows `"hello world"` (delete reversed) |
| 4 | Click **Redo**. | Editor shows `"hello"` (delete reapplied) |

---

## TC-03: Replace Text → Undo → Redo

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert `"hello world"` at position 0. | Editor shows `"hello world"` |
| 2 | Click **Replace**. Position `0`, length `5`, new text `"hi"`. | Editor shows `"hi world"` |
| 3 | Click **Undo**. | Editor shows `"hello world"` (replace reversed) |
| 4 | Click **Redo**. | Editor shows `"hi world"` (replace reapplied) |

---

## TC-04: Undo → New Operation → Redo Must Be Cleared

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert `"AAA"` at position 0. | Editor: `"AAA"` |
| 2 | Insert `"BBB"` at position 3. | Editor: `"AAABBB"` |
| 3 | Click **Undo**. | Editor: `"AAA"` (BBB removed). Redo count = 1. |
| 4 | Insert `"CCC"` at position 3. | Editor: `"AAACCC"`. **Redo count = 0** (cleared). |
| 5 | Click **Redo**. | Nothing happens. Status: "Nothing to redo". |

---

## TC-05: Type "hello" Continuously → Undo Removes Whole Group

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click into the textarea. Type `h`, `e`, `l`, `l`, `o` quickly (< 800ms gaps). | Editor: `"hello"`. History shows 5 INSERT operations with same `groupId`. |
| 2 | Click **Undo** once. | Editor: `""`. All 5 characters undone as one group. |
| 3 | Click **Redo** once. | Editor: `"hello"`. All 5 characters redone as one group. |

---

## TC-06: Type "hello", Wait > 800ms, Type "A" → Undo Removes Only "A"

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Type `h`, `e`, `l`, `l`, `o` quickly. | Editor: `"hello"` |
| 2 | Wait more than 800ms. | (pause) |
| 3 | Type `A`. | Editor: `"helloA"` |
| 4 | Click **Undo** once. | Editor: `"hello"` (only `"A"` removed — different group). |
| 5 | Click **Undo** again. | Editor: `""` (the `"hello"` group removed). |

---

## TC-07: Delete Operation Should Not Be Grouped with Insert

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Type `"abc"` quickly. | Editor: `"abc"`. One group (3 inserts). |
| 2 | Press Backspace (deletes `"c"`). | Editor: `"ab"`. This is a DELETE — separate from insert group. |
| 3 | Click **Undo** once. | Editor: `"abc"` (delete undone — only the delete, not the group). |
| 4 | Click **Undo** again. | Editor: `""` (insert group undone). |

---

## TC-08: Replace Operation Should Not Be Grouped with Insert

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Type `"hello"` quickly. | Editor: `"hello"` |
| 2 | Click **Replace**. Position `0`, length `5`, new text `"hi"`. | Editor: `"hi"`. Replace is a separate operation. |
| 3 | Click **Undo** once. | Editor: `"hello"` (replace undone only). |
| 4 | Click **Undo** again. | Editor: `""` (insert group undone). |

---

## TC-09: Save Document → Load Document

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Type `"Hello CSD201"`. | Editor: `"Hello CSD201"` |
| 2 | Click **Save**. | Status: "Document saved". |
| 3 | Clear the editor (select all + delete or type something else). | Editor content changes. |
| 4 | Click **Load**. | Editor: `"Hello CSD201"` (restored). Status: "Document loaded". |

---

## TC-10: Load Document Resets Undo/Redo History

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Type `"AAA"`, then `"BBB"`. | History shows 6 operations. |
| 2 | Click **Save**. | Document saved. |
| 3 | Type `"CCC"`. | History shows 9 operations. |
| 4 | Click **Load**. | Editor: `"AAABBB"`. **History viewer is empty** (reset). |
| 5 | Click **Undo**. | Nothing happens. Status: "Nothing to undo". |

---

## TC-11: Search Keyword Highlights Result

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert `"hello world hello again"`. | Editor shows the text. |
| 2 | Type `"hello"` in the search box. Click **Search**. | Status: "2 match(es) found for \"hello\"". |
| 3 | Type `"xyz"` in the search box. Click **Search**. | Status: "0 match(es) found for \"xyz\"". |

---

## TC-12: Dark Mode Toggles UI

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | App starts in light mode. | Background is light, text is dark. |
| 2 | Click the **🌙 Theme** button. | Background turns dark, text turns light. Button shows ☀. |
| 3 | Click the **☀ Theme** button again. | Returns to light mode. |
| 4 | Refresh the page. | Theme preference is preserved (via localStorage). |

---

## TC-13: RQ1 Benchmark Produces Table

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click **Run RQ1**. | Loading spinner shows. |
| 2 | Wait for completion. | Status: "Benchmark completed". |
| 3 | Benchmark table appears with columns: Model, Ops, Memory (MB), Undo Levels, Evicted, Notes. | ✓ |
| 4 | Table has 6 rows: BoundedStack × 3 counts + LRUStack × 3 counts. | ✓ |
| 5 | BoundedStack at 1000 ops: Undo Levels = 500, Evicted = 500. | ✓ |

---

## TC-14: RQ2 Benchmark Uses 10MB Text

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click **Run RQ2**. | Loading spinner shows (may take a few seconds). |
| 2 | Wait for completion. | Status: "Benchmark completed". |
| 3 | Table shows CommandHistory and SnapshotHistory rows. | ✓ |
| 4 | CommandHistory memory is small (< 1 MB). Under 50MB = **YES**. | ✓ |
| 5 | SnapshotHistory memory is very large (>> 50 MB). Under 50MB = **NO**. | ✓ |
| 6 | Notes mention "10MB" initial text. | ✓ |

---

## TC-15: RQ3 Benchmark Shows Average Undo/Redo Time

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Click **Run RQ3**. | Loading spinner shows. |
| 2 | Wait for completion. | Status: "Benchmark completed". |
| 3 | Table shows TwoStackHistory and DequeHistory rows. | ✓ |
| 4 | Columns include: Avg Undo (ms), Avg Redo (ms), Total (ms). | ✓ |
| 5 | All timing values are > 0 and come from actual execution. | ✓ |
| 6 | Notes mention 1000 operations and `System.nanoTime()`. | ✓ |

---

## Additional Edge-Case Tests

### TC-16: Undo on Empty Stack

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open fresh app (no operations). | Editor empty. |
| 2 | Click **Undo**. | Status: "Nothing to undo". App does NOT crash. |

### TC-17: Redo on Empty Stack

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open fresh app (no operations). | Editor empty. |
| 2 | Click **Redo**. | Status: "Nothing to redo". App does NOT crash. |

### TC-18: History Viewer Click Reverts State

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Insert `"A"`, `"B"`, `"C"` (3 separate button inserts). | Editor: `"ABC"`. History: 3 items. |
| 2 | Click on action #1 in the History Viewer. | Editor: `"A"` (undone to after action 1). |
| 3 | State was reached by **repeated undo**, not by removing middle operations. | ✓ |

### TC-19: Export Benchmark Results

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Run any benchmark (e.g., RQ3). | Results table appears. |
| 2 | Click **Export Results**. | CSV file downloads with benchmark data. |
| 3 | Open CSV — data matches the displayed table. | ✓ |

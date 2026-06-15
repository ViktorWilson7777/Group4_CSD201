# CSD201 — Undo/Redo Text Editor Simulator

A **web-based Undo/Redo text editor** built as a CSD201 Data Structures project.  
It demonstrates **Stack, Command Pattern, Deque, Snapshot comparison**, and **benchmark research** through a clean browser UI backed by Java Servlets.

---

## 1. Project Goal

Simulate Undo and Redo behavior (like Word or VS Code) at a simplified academic level.  
The core focus is on **data structures**: Stack, Operation-based history, Command Pattern, Deque, Snapshot comparison, and benchmark analysis for three research questions.

> **Important**: This project does **not** use the browser's native Ctrl+Z / Ctrl+Y logic.  
> The app maintains its own `undoStack` and `redoStack` (or equivalent history model) implemented in Java.

---

## 2. How to Run in NetBeans

### Prerequisites
- **JDK 8+**
- **Apache Maven 3.6+**
- **NetBeans IDE** (with Maven and Java Web support)
- **Apache Tomcat 9+** configured in NetBeans

### Steps

1. **Open the project**  
   `File → Open Project` → select the `csd201-undo-redo` folder (NetBeans will recognize the `pom.xml`).

2. **Set Tomcat as the server**  
   Right-click project → `Properties → Run` → set Server to your Tomcat instance.

3. **Build & Run**  
   Right-click project → `Run` (or press `F6`).  
   NetBeans will execute `mvn clean package`, deploy the WAR to Tomcat, and open `index.jsp` in your browser.

4. **Access the app**  
   Navigate to: `http://localhost:8080/csd201-undo-redo/`

### Command-line build
```bash
mvn clean package
```
The WAR file is generated at `target/csd201-undo-redo-1.0-SNAPSHOT.war`.

---

## 3. How to Run Using a Local Server (if Tomcat fails)

If Tomcat is not available, you can deploy the WAR to any Servlet container, or use the Maven Cargo plugin:

```bash
# Option 1: Copy WAR to any Tomcat's webapps/ folder
cp target/csd201-undo-redo-1.0-SNAPSHOT.war /path/to/tomcat/webapps/

# Option 2: Use an embedded server (requires additional plugin)
# Add tomcat7-maven-plugin to pom.xml, then:
mvn tomcat7:run
```

> **Note**: The app requires a Servlet container because it uses Java Servlets for backend logic. A simple file server (like `python -m http.server`) will NOT work.

---

## 4. Main Features

| Feature | Description |
|---------|-------------|
| **Text Editor** | Editable textarea with live change detection |
| **Insert / Delete / Replace** | Manual operations via modal dialogs |
| **Undo / Redo** | Custom stack-based undo/redo with grouped actions |
| **Action History Viewer** | Visual panel showing all operations; click to revert |
| **Save / Load** | Persist document to session; loading resets history |
| **Search** | Find and count keyword matches |
| **Dark Mode** | Toggle between light and dark themes |
| **Benchmark Panel** | Run RQ1, RQ2, RQ3 benchmarks with real results |
| **Export Results** | Download benchmark results as CSV |
| **History Model Selector** | Switch between CommandHistory, SnapshotHistory, TwoStackHistory, DequeHistory |

---

## 5. Why Stack Is Used

A **Stack** (Last-In-First-Out) is the natural data structure for undo/redo:

- **Undo** always reverses the **most recent** action → pop from `undoStack`.
- **Redo** reapplies the **most recently undone** action → pop from `redoStack`.
- New operations push onto `undoStack` and **clear** `redoStack`.

This LIFO ordering ensures that the user always undoes/redoes in the correct chronological order.

---

## 6. What Operation Means

An `Operation` is an object that encapsulates a single text change:

```
Operation
├── type       (INSERT / DELETE / REPLACE)
├── position   (character index)
├── oldText    (text before the change — used for undo)
├── newText    (text after the change — used for redo/execute)
├── timestamp  (when the operation occurred)
└── groupId    (for grouped undo of continuous typing)
```

Each `Operation` knows how to **execute** itself (apply the change) and **undo** itself (reverse the change) on a `TextEditor`.

---

## 7. Why Operation Represents the Command Pattern

The **Command Pattern** encapsulates a request as an object, allowing:

1. **Parameterization**: Each `Operation` stores all data needed to execute and reverse.
2. **Undo support**: The `undo()` method reverses `execute()` using stored `oldText`.
3. **Queueing**: Operations are stored in stacks for history traversal.
4. **Decoupling**: The `TextEditor` doesn't know about undo/redo; it just executes text mutations.

**Concrete commands**: `InsertOperation`, `DeleteOperation`, `ReplaceOperation`.

---

## 8. Difference Between CommandHistory and SnapshotHistory

| Aspect | CommandHistory | SnapshotHistory |
|--------|---------------|-----------------|
| **Stores** | `Operation` objects (type, position, text diffs) | Full `String` snapshots of entire document |
| **Memory** | Proportional to **change size** | Proportional to **document size × operation count** |
| **Undo mechanism** | Calls `op.undo(editor)` to reverse the change | Restores a previous full snapshot |
| **10MB document + 100 ops** | ~few KB (only stores diffs) | ~1 GB+ (100 copies of ~10MB) |
| **RQ2 conclusion** | ✅ Stays well under 50MB | ❌ Exceeds 50MB rapidly |

---

## 9. Difference Between BoundedStackHistory and LRUStackHistory

| Aspect | BoundedStackHistory | LRUStackHistory |
|--------|--------------------|--------------------|
| **Eviction trigger** | Operation **count** exceeds limit N (default 500) | Total **memory bytes** exceed threshold (default 50MB) |
| **Eviction target** | Remove **oldest** operation from front | Remove **oldest** operation from front |
| **Predictability** | Always keeps exactly N undo levels | Undo levels vary based on operation sizes |
| **Use case** | Simple, predictable limit | Adaptive to operation sizes |
| **RQ1 focus** | Compare undo levels and memory at 500/1000/2000 ops | Same comparison |

---

## 10. Difference Between TwoStackHistory and DequeHistory

| Aspect | TwoStackHistory | DequeHistory |
|--------|----------------|--------------|
| **Data structure** | Two separate `Stack<Operation>` (undo + redo) | One `ArrayList<Operation>` + `currentIndex` |
| **Undo** | Pop from undoStack, push to redoStack | Decrement `currentIndex` |
| **Redo** | Pop from redoStack, push to undoStack | Increment `currentIndex` |
| **New operation** | Push to undoStack, clear redoStack | Truncate list after `currentIndex`, append |
| **RQ3 focus** | Compare average undo/redo timing | Same comparison |

---

## 11. How Group Actions Work

**Grouped undo** combines continuous typing into a single undo step.

### Grouping conditions (ALL must be true):
1. Previous operation exists
2. Previous operation type is **INSERT**
3. Current operation type is **INSERT**
4. Time difference ≤ **800ms**
5. Current position = previous position + previous newText length (cursor is adjacent)

### What is NOT grouped:
- `DeleteOperation` — always starts a new group (`groupId = null`)
- `ReplaceOperation` — always starts a new group (`groupId = null`)
- Insert after a pause > 800ms — starts a new group
- Insert at a non-adjacent position — starts a new group

### Undo group behavior:
1. Look at the top of `undoStack`, get its `groupId`
2. Undo that operation
3. Continue undoing while the next top has the **same `groupId`**
4. Stop when `groupId` changes or stack is empty
5. Always follows **LIFO** — never removes from the middle

### Redo group behavior:
- Same logic applied to `redoStack` — redo all operations with matching `groupId`

---

## 12. How BenchmarkRunner Answers RQ1, RQ2, RQ3

### RQ1: Count-based vs Memory-based Eviction
- **Models**: BoundedStackHistory (limit=500) vs LRUStackHistory (maxMB=50)
- **Test counts**: 500, 1000, 2000 operations
- **Metrics**: memory usage, undo levels retained, evicted count
- **Fairness**: Same deterministic operation list for both models

### RQ2: Operations vs Snapshots on Large Documents
- **Models**: CommandHistory vs SnapshotHistory
- **Setup**: 10MB initial text + 100 operations
- **Metrics**: memory usage, whether under 50MB threshold
- **Key insight**: CommandHistory stores only diffs (~KB), SnapshotHistory stores full copies (~GB)

### RQ3: Two-Stack vs Deque Timing
- **Models**: TwoStackHistory vs DequeHistory
- **Setup**: 1000 operations, then undo all, then redo all
- **Metrics**: average undo time, average redo time, total time
- **Timing**: `System.nanoTime()` for nanosecond precision

> All benchmark numbers come from **actual code execution** — no fake or pre-computed data.

---

## 13. Evidence Checklist

| # | Evidence | Status |
|---|----------|--------|
| 1 | Insert + Undo + Redo works | ☐ |
| 2 | Delete + Undo + Redo works | ☐ |
| 3 | Replace + Undo + Redo works | ☐ |
| 4 | New operation clears redo stack | ☐ |
| 5 | Grouped undo for continuous typing | ☐ |
| 6 | Group broken by time gap > 800ms | ☐ |
| 7 | Delete not grouped with insert | ☐ |
| 8 | Replace not grouped with insert | ☐ |
| 9 | Save and Load document works | ☐ |
| 10 | Load resets undo/redo history | ☐ |
| 11 | Search highlights results | ☐ |
| 12 | Dark mode toggles UI | ☐ |
| 13 | RQ1 benchmark produces table | ☐ |
| 14 | RQ2 benchmark uses 10MB text | ☐ |
| 15 | RQ3 benchmark shows timing | ☐ |
| 16 | History viewer shows operations | ☐ |
| 17 | History viewer click reverts state | ☐ |
| 18 | Export benchmark results as CSV | ☐ |

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 8 + Servlet 4.0 |
| Frontend | JSP + HTML5 + CSS3 + Vanilla JavaScript |
| Build | Maven 3 (WAR packaging) |
| Server | Apache Tomcat 9+ |
| Data | HttpSession (no database) |
| Serialization | Gson 2.10.1 |

---

## Project Structure

```
csd201-undo-redo/
├── pom.xml
├── README.md
├── TEST_CASES.md
├── src/main/
│   ├── java/com/fpt/csd201/
│   │   ├── editor/
│   │   │   ├── Operation.java          (abstract Command Pattern base)
│   │   │   ├── InsertOperation.java
│   │   │   ├── DeleteOperation.java
│   │   │   ├── ReplaceOperation.java
│   │   │   └── TextEditor.java
│   │   ├── history/
│   │   │   ├── HistoryStrategy.java     (interface)
│   │   │   ├── CommandHistory.java      (main model)
│   │   │   ├── SnapshotHistory.java     (RQ2)
│   │   │   ├── BoundedStackHistory.java (RQ1)
│   │   │   ├── LRUStackHistory.java     (RQ1)
│   │   │   ├── TwoStackHistory.java     (RQ3)
│   │   │   ├── DequeHistory.java        (RQ3)
│   │   │   └── UndoRedoManager.java     (coordinator)
│   │   ├── benchmark/
│   │   │   └── BenchmarkRunner.java
│   │   └── servlet/
│   │       ├── EditorServlet.java
│   │       └── BenchmarkServlet.java
│   └── webapp/
│       ├── index.jsp
│       ├── styles.css
│       ├── js/app.js
│       └── WEB-INF/web.xml
├── docs/
├── evidence/
└── benchmark-results/
```

# CSD201 Undo/Redo Text Editor — Project Rules

## Tổng quan
Đây là đồ án môn CSD201 (Data Structures & Algorithms) — hệ thống soạn thảo văn bản có tính năng Undo/Redo, áp dụng Command Pattern và các cấu trúc dữ liệu Stack/Deque. Hệ thống có 2 phiên bản: Web (JSP + Servlet) và Console (Java Terminal).

## Công nghệ
- **Ngôn ngữ:** Java 8+
- **Build:** Maven (`pom.xml`)
- **Web Server:** Tomcat 7 (chạy bằng `mvn tomcat7:run`)
- **Frontend:** HTML/CSS/JavaScript thuần (không framework)
- **Thư viện:** Gson 2.10.1 (JSON parsing)

## Kiến trúc Package

```
com.fpt.csd201
├── editor/          ← TextEditor + Command Pattern (Operation classes)
├── history/         ← 6 History Models + UndoRedoManager
├── benchmark/       ← BenchmarkRunner cho RQ1/RQ2/RQ3
├── servlet/         ← EditorServlet + BenchmarkServlet (Web API)
└── console/         ← ConsoleApp (Java Console version)
```

## Các class quan trọng

### Package `editor` — KHÔNG ĐƯỢC SỬA ĐỔI
| Class | Vai trò |
|-------|---------|
| `TextEditor` | Lưu trữ nội dung văn bản (`String content`). Có 3 hàm: `insertText()`, `deleteText()`, `replaceText()` |
| `Operation` (abstract) | Base class cho Command Pattern. Lưu `type`, `position`, `oldText`, `newText`, `timestamp`, `groupId` |
| `InsertOperation` | Lệnh chèn chữ. `execute()` = insert, `undo()` = delete |
| `DeleteOperation` | Lệnh xóa chữ. `execute()` = delete, `undo()` = insert lại |
| `ReplaceOperation` | Lệnh thay thế. `execute()` = replace, `undo()` = replace ngược |

### Package `history` — KHÔNG ĐƯỢC SỬA ĐỔI
| Class | RQ | Mô tả |
|-------|----|-------|
| `HistoryStrategy` (interface) | — | Giao diện chung: `record()`, `undo()`, `redo()`, `getMemoryUsage()`, etc. |
| `UndoRedoManager` | — | Điều phối Undo/Redo có nhóm (grouped undo/redo theo `groupId`) |
| `CommandHistory` | RQ2 | Stack lưu từng Operation object |
| `SnapshotHistory` | RQ2 | Stack lưu toàn bộ snapshot văn bản |
| `BoundedStackHistory` | RQ1 | Stack giới hạn số lượng (mặc định 500) |
| `LRUStackHistory` | RQ1 | Stack giới hạn dung lượng bộ nhớ (mặc định 50MB) |
| `TwoStackHistory` | RQ3 | Hai Stack riêng biệt (undoStack + redoStack) |
| `DequeHistory` | RQ3 | Dùng Deque + currentIndex |

### Package `benchmark` — KHÔNG ĐƯỢC SỬA ĐỔI
| Class | Vai trò |
|-------|---------|
| `BenchmarkRunner` | Chạy benchmark cho RQ1 (BoundedStack vs LRU), RQ2 (Command vs Snapshot trên 10MB text), RQ3 (TwoStack vs Deque timing) |

## Quy tắc quan trọng

### 1. Command Pattern
- Mỗi thao tác người dùng (gõ/xóa/thay thế) PHẢI được đóng gói thành một `Operation` object.
- `Operation.execute()` = thực thi thao tác, `Operation.undo()` = đảo ngược thao tác.
- Tất cả Operation đều implements `Serializable`.

### 2. Khi tạo DeleteOperation
- PHẢI trích xuất `oldText` từ `editor.getContent()` TRƯỚC khi gọi `record()`.
- Vì `DeleteOperation` cần lưu chữ bị xóa để có thể Undo (chèn lại).

### 3. Khi đổi History Model
- Tạo model mới bằng constructor mặc định (tất cả 6 model đều có no-arg constructor).
- Gọi `manager.setHistoryModel(newModel)` để chuyển.
- Lịch sử Undo/Redo cũ sẽ bị mất khi đổi model.

### 4. Frontend (Web) — Package `servlet` + thư mục `webapp`
- `EditorServlet` nhận JSON qua HTTP POST, xử lý rồi trả JSON.
- Frontend (`app.js`) dùng thuật toán Diff (`detectChange()`) để so sánh văn bản trước/sau khi gõ phím, từ đó xác định loại Operation (insert/delete/replace).
- CSS nằm trong `styles.css`, hỗ trợ Dark/Light mode.

### 5. Console App — Package `console`
- `ConsoleApp.java` chứa hàm `main()`, dùng `Scanner` để đọc input từ Terminal.
- Tái sử dụng 100% code từ package `editor`, `history`, `benchmark`.
- Sau mỗi thao tác, xóa màn hình (`\033[H\033[2J`) và in lại giao diện mới.

## Research Questions (RQ)
- **RQ1:** BoundedStackHistory vs LRUStackHistory — So sánh giới hạn theo số lượng vs giới hạn theo bộ nhớ tại 500/1000/2000 operations.
- **RQ2:** CommandHistory vs SnapshotHistory — So sánh Command Pattern (lưu Operation) vs Snapshot (lưu toàn bộ text) trên file 10MB.
- **RQ3:** TwoStackHistory vs DequeHistory — So sánh tốc độ Undo/Redo giữa hai cấu trúc dữ liệu.

## Lệnh thường dùng
```bash
mvn compile                    # Biên dịch
mvn tomcat7:run                # Chạy Web (http://localhost:8080)
mvn exec:java -Dexec.mainClass="com.fpt.csd201.console.ConsoleApp"  # Chạy Console
```

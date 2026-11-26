# FileGuard — Viva preparation

The viva checks **individual understanding**, so every team member should be able to open the right file and explain it in their own words. Read this together and practise answering out loud.

## 1. "Show me where…" — code map

| If they ask about… | Open | Point at |
|---|---|---|
| Where the program starts | `Launcher.java` → `FileGuardApp.java` | `start()`: open DB → build window → connect backend in background |
| How a file is hashed | `core/FileHasher.java` | `MessageDigest.getInstance("SHA-256")`, 64 KB buffer loop, `checkCancelled()` per chunk |
| How folders are scanned | `core/FolderScanner.java` | `Files.walkFileTree`, `visitFileFailed` → Skipped, relative paths with `/` |
| Added / modified / deleted logic | `core/IntegrityChecker.java` | two maps by relative path; compare hashes |
| Backup comparison | `core/BackupVerifier.java` | `compareSizes` → `addFilesOnlyInBackup` → `compareContents` |
| Duplicate detection | `core/DuplicateFinder.java` | group by size → hash candidates → group by hash; `redundantBytes()` |
| Why the UI doesn't freeze | `ui/ScanTask.java`, `ui/UiUtil.runScan` | `Task` on a daemon thread; `updateProgress` / `updateMessage`; `setOnSucceeded` runs on the FX thread |
| How Cancel works | `ui/components/ScanProgressPanel.java` → `core/ProgressListener.java` | `task.cancel(false)` → `isCancelled()` → `CancellationException` |
| Saving a scan | `service/ScanService.execute` | timing, save in history, log to backend, catch cancel/failure |
| Database tables | `db/Database.initialize` | four `CREATE TABLE` statements, foreign keys, cascade |
| SQL / JDBC | `db/ScanHistoryDao.save` | `PreparedStatement`, `addBatch`, transaction with `commit`/`rollback` |
| The backend | `server/BackendServer.java` | `HttpServer.create(loopback, 8765)`, `createContext`, endpoints, `safe()` error handling |
| Backend database | `server/LogRepository.java` | own SQLite file, `insert`, `find` with optional filters |
| App → backend communication | `client/BackendClient.java` | `HttpClient.sendAsync`, offline queue, `checkHealth` every 5 s |
| JSON without a library | `server/Json.java` | `write` (escaping) and a small recursive parser with a depth limit |
| CSV export | `core/ReportExporter.java` | header block, `escape()` (quotes, formula guard), UTF-8 BOM |
| Themes | `ui/ThemeManager.java`, `css/theme-*.css` | swaps one stylesheet; `Preferences` remembers the choice |
| Building the .exe | `packaging/build-windows-app.bat` | `mvnw package` → `jpackage --type app-image` with a bundled runtime |

## 2. Concepts you must be able to explain

**SHA-256 / hashing.** A hash function turns any file into a fixed 64-character fingerprint. Changing even one bit gives a completely different fingerprint, and two different files having the same fingerprint is practically impossible. So "same hash ⇒ same content", without comparing the files byte by byte every time.

**Why stream files in chunks?** Reading a whole 6 GB file into memory would crash the program. We read 64 KB at a time and feed each chunk to the digest, so memory use stays tiny for any file size.

**Baseline.** A snapshot of a folder at one moment: relative path, size, modified time and hash of each file, stored in SQLite. A later check hashes the files again and compares.

**Relative paths.** The backup can be on another drive, so `D:\Photos\a.jpg` and `E:\Backup\a.jpg` are matched as the same relative path `a.jpg`.

**Threads in JavaFX.** All screen updates must happen on the JavaFX Application Thread. Long work runs on a background thread inside a `Task`, which safely sends progress back to the screen. If we hashed on the UI thread, the window would freeze ("Not responding").

**Cooperative cancellation.** We don't kill the thread. The Cancel button sets a flag; the hashing loop checks it after every chunk and stops cleanly by throwing `CancellationException`. Nothing half-finished is saved.

**JDBC and prepared statements.** JDBC is Java's standard database API. A `PreparedStatement` sends the SQL and the values separately, so a file name like `x'; DROP TABLE scans;--` is stored as text and can't change the SQL (SQL injection).

**Transactions.** Saving a baseline deletes the old one and inserts hundreds of rows. Inside one transaction it either all happens or none of it does, so a crash halfway can't leave a broken baseline.

**REST backend.** A server that exposes data through HTTP URLs and methods: GET to read, POST to create, DELETE to remove. It exchanges JSON. Ours runs on `127.0.0.1` (this computer only) at port 8765.

**Why a separate backend database?** Each service owns its data, so the backend can be stopped, replaced or run separately (`FileGuard Backend.exe`) without touching the app's scan history.

**Offline queue.** If the backend is down, logs are kept in a queue in memory. A health check every 5 seconds notices when it's back and sends them.

## 3. Object-oriented design — where each idea appears

| Concept | Example in FileGuard |
|---|---|
| **Encapsulation** | `BackendClient` hides HTTP details; callers just call `log(...)`. DAOs hide all SQL. |
| **Abstraction / interfaces** | `ProgressListener` and `ActivityLogger`: the scanning code depends on these, not on JavaFX or HTTP. |
| **Polymorphism** | `ScanTask` implements `ProgressListener` in the app; tests pass `ProgressListener.NONE` or a lambda instead. `BackendClient` implements `ActivityLogger`; tests use `ActivityLogger.NONE`. |
| **Inheritance** | `ScanTask extends Task<ScanReport>`; every page `extends StackPane`; `StatCard extends VBox`. |
| **Immutability** | Records (`ScanReport`, `FileEntry`, `ResultItem`, `LogEntry`) can't be changed after creation; `withHash()` / `withId()` return new copies. |
| **Single responsibility** | One class per job: `FileHasher` only hashes, `ReportExporter` only writes CSV, `LogRepository` only stores logs. |
| **Enums** | `ScanType`, `ScanStatus`, `ResultStatus`, `LogLevel`: fixed sets of values with labels. |

## 4. Likely questions (with short answers)

| Question | Answer |
|---|---|
| Why SHA-256 and not MD5? | MD5 is broken (collisions can be created on purpose). SHA-256 is the current standard and built into Java. |
| Can FileGuard detect corruption? | It detects that content **changed**. Whether that's corruption or an intended edit, only the user can decide, so we never call it corruption. |
| Why compare sizes before hashing in backup verification? | Different sizes already prove the files differ, so hashing is skipped and large files aren't read for nothing. |
| What happens with a file you can't open? | It's listed as *Skipped* with the reason (e.g. access denied), the scan continues, and it isn't reported as deleted. |
| Why SQLite? | No server to install or configure; a single file created automatically; full SQL and JDBC support. |
| What if you pick the backup inside the original folder? | Rejected before scanning, because the backup files would be compared with themselves. |
| How do you stop other computers using the backend? | It binds only to `127.0.0.1`. It also validates input, limits body size (16 KB) and JSON depth, and only accepts JSON posts. |
| What's in a REST response? | Always `{"success": ..., "data": ..., "error": ...}` with a proper HTTP status (200, 201, 400, 404, 405, 413, 415, 500). |
| How did you test it? | 36 JUnit tests (unit and integration with real files, SQLite and HTTP), 88% line coverage, and a UI test plan. See `docs/TESTING.md`. |
| What would you add next? | Scheduled scans, comparing two baselines over time, an installer (MSI), and exporting to PDF. |
| How does the .exe run without Java installed? | `jpackage` bundles a trimmed Java runtime (only the modules we need) inside the app folder. |
| What was the hardest bug? | (1) Error replies broke the backend connection because try-with-resources closed the exchange *before* the catch block. (2) The build script failed on PCs that block running programs from the current folder by name, fixed by using the full path. |

## 5. Suggested split for a team

Each person owns one area, can demo it, and can answer its questions:

1. **Design & UI**: DESIGN.md diagrams, JavaFX pages, themes, ScanTask and Cancel.
2. **Core logic**: hashing, integrity, backup, duplicates (the `core` package).
3. **Database & backend**: SQLite schema, DAOs, BackendServer, BackendClient, REST endpoints.
4. **Testing & packaging**: the 36 tests, coverage, TESTING.md, building `FileGuard.exe`.

Everyone should still be able to explain the overall flow: **click → background task → ScanService → core → database → backend log → results on screen**.

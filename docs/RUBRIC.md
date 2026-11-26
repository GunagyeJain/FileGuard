# FileGuard — Evidence for each evaluation criterion

This page maps every line of the **Lab Evaluation-II** rubric to what FileGuard provides and where to show it during the evaluation.

| # | Criterion | Marks |
|---|---|---|
| 1 | Problem Identification & Project Design | 2 |
| 2 | Front-End Development | 2 |
| 3 | Back-End Development & Database Integration | 3 |
| 4 | Functionality & Feature Implementation | 3 |
| 5 | Code Quality & Best Practices | 2 |
| 6 | Testing & Validation | 1 |
| 7 | Demonstration & Viva | 2 |
| | **Total** | **15** |

---

## 1. Problem Identification & Project Design (2)

| Evidence | Where |
|---|---|
| Real problem: silent file changes, incomplete backups, wasted space | [DESIGN.md §1](DESIGN.md#1-problem-statement) |
| 8 measurable objectives, explicit out-of-scope list | [DESIGN.md §2](DESIGN.md#2-objectives) |
| Use-case diagram | [DESIGN.md §3](DESIGN.md#3-users-and-use-cases) |
| Layered architecture diagram + responsibility table | [DESIGN.md §4](DESIGN.md#4-architecture) |
| Class diagram of the main classes and interfaces | [DESIGN.md §5](DESIGN.md#5-class-design-main-classes) |
| ER diagrams for both databases + design decisions | [DESIGN.md §6](DESIGN.md#6-database-design) |
| Sequence diagram of an integrity check | [DESIGN.md §7](DESIGN.md#7-key-flow--integrity-check) |
| Algorithms with reasoning, error-handling strategy | [DESIGN.md §8–9](DESIGN.md#8-algorithms-and-their-cost) |

**Show:** open `docs/DESIGN.md` on GitHub (the diagrams render automatically).

## 2. Front-End Development (2)

| Evidence | Where |
|---|---|
| JavaFX desktop UI with 7 pages and sidebar navigation | `ui/MainWindow.java`, `ui/views/*` |
| Polished dashboard look: indigo accent, rounded cards with icons, light and dark theme, remembered | `resources/.../css/theme-light.css`, `theme-dark.css`, `ui/ThemeManager.java` |
| All styling in CSS (no inline colours); reusable components | `css/base.css`, `ui/components/*` |
| Responsive UI: scans in the background with progress, current file and Cancel | `ui/ScanTask.java`, `ui/components/ScanProgressPanel.java` |
| Clear feedback: summary numbers, status labels, tables, empty states, confirmations | every page |

**Show:** the Dashboard, then switch to dark theme in Settings. Start a scan and move between pages while it runs.

## 3. Back-End Development & Database Integration (3)

| Evidence | Where |
|---|---|
| Separate **REST backend** (JDK HTTP server) on `127.0.0.1:8765` | `server/BackendServer.java` |
| Endpoints: `GET/POST/DELETE /api/logs`, `GET /api/stats`, `GET /api/health`, web viewer | README "Local backend" table |
| Input validation, size limits, JSON-only POST, exact paths, standard response envelope | `BackendServer.java` (`requireText`, `requireJson`, `requirePath`, `safe`) |
| HTTP client with async sends, offline queue, live status | `client/BackendClient.java` |
| **Two SQLite databases**, created automatically: app data + backend logs | `db/Database.java`, `server/LogRepository.java` |
| DAO layer, `PreparedStatement` everywhere, transactions, `ON DELETE CASCADE`, indexes | `db/BaselineDao.java`, `db/ScanHistoryDao.java` |
| Data survives restarts (tested) | `ScanServiceTest.dataSurvivesReopeningTheDatabase` |

**Show:** the Activity log page → **Open in browser** → `http://127.0.0.1:8765/api/logs` (raw JSON). Bonus: run `FileGuard Backend.exe` as a separate process.

## 4. Functionality & Feature Implementation (3)

| Feature | Where | Demo step |
|---|---|---|
| File integrity: baseline + added/modified/deleted | `core/IntegrityChecker.java`, `ScanService.checkIntegrity` | DEMO steps 4–6 |
| Backup verification by relative path + content | `core/BackupVerifier.java` | DEMO step 2 |
| Duplicate detection + redundant storage | `core/DuplicateFinder.java` | DEMO step 3 |
| Scan history (persists) | `db/ScanHistoryDao.java`, `ui/views/HistoryView.java` | DEMO step 9 |
| CSV report export | `core/ReportExporter.java` | DEMO step 7 |
| Background scanning with safe Cancel | `ui/ScanTask.java`, `core/ProgressListener.java` | DEMO step 8 |
| Activity logging to backend | `service/ScanService.execute`, `ui/views/LogsView.java` | DEMO step 10 |
| One-click Windows app | `packaging/build-windows-app.bat` | double-click `FileGuard.exe` |

Every feature in the original requirements is implemented, and nothing is a mock-up. The demo data produces the same verified numbers every time ([DEMO.md](../DEMO.md)).

## 5. Code Quality & Best Practices (2)

| Practice | Evidence |
|---|---|
| Separation of concerns (UI / service / core / db / server / client / model) | [DESIGN.md §4](DESIGN.md#4-architecture) |
| Interfaces to decouple layers | `ProgressListener` (core ↔ JavaFX), `ActivityLogger` (service ↔ HTTP) |
| Immutable data with Java records | `model/*` (`ScanReport`, `FileEntry`, `ResultItem`, `LogEntry`, ...) |
| Resources always closed (try-with-resources) | every DAO, `FileHasher`, `ReportExporter` |
| Streamed hashing (64 KB chunks), never whole files in memory | `core/FileHasher.java` |
| Thread safety: UI updates only on the FX thread; atomic online flag; background `Task`s for DB and network | `ui/UiUtil.background`, `BackendClient.setOnline` |
| Security: loopback-only server, validation, size and depth limits, SQL parameters, CSV-injection guard, XSS-safe viewer | `BackendServer`, `Json`, `ReportExporter`, `index.html` |
| Named constants instead of magic numbers; small focused methods; Javadoc on public classes | throughout |
| Files under 400 lines; methods short and named for what they do | e.g. `BackupVerifier` split into `compareSizes` / `addFilesOnlyInBackup` / `compareContents` |
| Independent code review performed and all findings fixed | git history (`refactor`/`fix` commits) |

## 6. Testing & Validation (1)

- **36 automated tests, all passing**: unit tests plus integration tests with real files, SQLite and HTTP.
- **88% line coverage** of all non-UI code (JaCoCo report in `target/site/jacoco/index.html`).
- **UI test plan** with expected and actual results for every screen.
- **Input validation** table.

All of it is documented in [TESTING.md](TESTING.md). Run it with `.\mvnw.cmd test`.

## 7. Demonstration & Viva (2)

- Step-by-step demo script with verified expected numbers: [DEMO.md](../DEMO.md).
- Reproducible demo data, reset in seconds: `demo\setup-demo.ps1`.
- Viva preparation: code map, concept explanations and likely questions: [VIVA.md](VIVA.md).

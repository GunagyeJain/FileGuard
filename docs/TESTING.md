# FileGuard — Testing & Validation

FileGuard is tested at three levels:

1. **Unit tests**: single classes, such as hashing, comparisons, JSON and formatting.
2. **Integration tests**: real files on disk, a real SQLite database and a real HTTP backend.
3. **UI tests**: every screen exercised through the real buttons of the running app.

All results below come from actual runs.

## How to run

```powershell
.\mvnw.cmd test
```

Then open `target/site/jacoco/index.html` for the coverage report. Building the app with `packaging\build-windows-app.bat` also runs every test first, and the build stops if any test fails.

## Results

| | |
|---|---|
| Automated tests | **36 passed, 0 failed, 0 skipped** |
| Line coverage (non-UI code) | **88%** |
| Branch coverage (non-UI code) | **75%** |

| Package | Line coverage |
|---|---|
| `model` | 100% |
| `core` (hashing, scanning, comparison, CSV) | 92% |
| `service` (scan orchestration) | 92% |
| `server` (backend) | 88% |
| `db` (JDBC data access) | 86% |
| `client` (HTTP client) | 74% |

The JavaFX screens (`ui` package) can't be measured by JaCoCo in a normal unit-test run. They're covered by the UI test plan in section 3.

## 1. Unit tests

### `FileHasherTest`: SHA-256 hashing
| Test | What it proves |
|---|---|
| `hashesKnownValues` | `"abc"` and an empty file give the official SHA-256 test-vector values (FIPS 180) |
| `largeFileSpanningManyBuffersMatchesInMemoryDigest` | A file larger than five 64 KB chunks hashes correctly, and every byte is reported for progress |
| `differentContentGivesDifferentHash` | A one-letter difference changes the hash |
| `stopsWhenCancelled` | Hashing stops with `CancellationException` when Cancel is pressed |

### `ComparisonTest`: integrity, backup and duplicate logic
| Test | What it proves |
|---|---|
| `detectsAddedModifiedAndDeleted` | Each change type is detected exactly once |
| `unreadableFileIsNotReportedAsDeleted` | A locked or unreadable file isn't wrongly reported as deleted |
| `identicalStateHasNoDifferences` | No false positives |
| `backupVerificationFindsMissingDifferentAndExtra` | Missing, different (same size *and* different size) and extra files are all found; matching files are counted |
| `backupVerificationRejectsSameOrNestedFolders` | Same folder twice, or the backup inside the original, is refused |
| `emptyFoldersMatch` | Empty folders are handled without errors |
| `findsDuplicateGroupsAndRedundantBytes` | Correct groups, group sizes and redundant bytes; same-size but different files and empty files are ignored |
| `noDuplicatesInUniqueFolder` | No false duplicates |
| `listingRejectsMissingFolder` | A missing folder gives a clear error |

### `JsonTest`: backend JSON helper
| Test | What it proves |
|---|---|
| `writesAndReadsBackNestedValues` | Round-trip of objects, arrays, numbers, booleans, null, quotes, newlines; `<` is escaped |
| `parsesUnicodeEscapes` | `é` becomes `é` |
| `rejectsInvalidJson` | Malformed input, trailing text and wrong types are rejected |
| `limitsNestingDepth` | Nesting deeper than 32 levels is rejected, so a malicious body can't crash the server |

### `FormatAndExportTest`: helpers
| Test | What it proves |
|---|---|
| `formatsByteSizes`, `formatsDurationsAndCounts` | Human-readable sizes, durations, plurals and dates |
| `escapesCsvValuesSafely` | Commas, quotes and newlines are quoted (RFC 4180); values starting with `= + - @ tab` are neutralised (CSV formula injection) |
| `activityLoggerShortcutsUseTheRightLevel` | `info` / `warn` / `error` map to the right log level |

## 2. Integration tests

### `ScanServiceTest`: real folders and a real SQLite database
| Test | What it proves |
|---|---|
| `integrityCheckDetectsChangesAfterBaseline` | Full flow: baseline → edit, delete and add files → check finds 1 added, 1 modified, 1 deleted |
| `integrityCheckWithoutBaselineIsRejected` | Clear message instead of a crash |
| `dataSurvivesReopeningTheDatabase` | **Persistence:** new `Database` and `ScanService` objects on the same file (the same as restarting the app) still see the baseline, history and stored results |
| `newBaselineReplacesOldOne` | Only one baseline per folder; the replacement is transactional |
| `cancelledScanIsRecordedAndSavesNoBaseline` | **Cancellation:** a scan cancelled mid-way saves no baseline and records a "Cancelled" history entry |
| `duplicatesAndBackupAreStored` | Duplicate and backup scans are saved, and redundant bytes are queryable for the dashboard |
| `exportsCsvReport` | **Report export:** the CSV contains the header, the rows, quoted values and the disclaimer |

### `BackendServerTest`: a real backend on a free port, called over HTTP
| Test | What it proves |
|---|---|
| `healthIdentifiesTheService` | `GET /api/health` answers and identifies FileGuard |
| `storesAndListsLogsWithFilters` | `POST` stores logs; `GET` returns them newest first, filtered by level; `/api/stats` counts are right |
| `rejectsInvalidRequests` | Bad level, missing field, bad category, invalid JSON, bad limit → `400`; wrong method → `405`; unknown path → `404`; body over 16 KB → `413`; errors use the standard envelope |
| `rejectsRequestsThatCouldComeFromAWebPageOrLookAlikePaths` | `text/plain` POST (possible from any web page) → `415`; `/api/logsXYZ` → `404`; 5,000-deep JSON → clean `400`, nothing stored |
| `clearDeletesEverything` | `DELETE /api/logs` empties the log |
| `servesTheLogViewerPage` | `GET /` returns the browser log viewer |
| `clientQueuesLogsWhileOfflineAndSendsThemLater` | **Resilience:** with the backend stopped, logs are queued; when it starts again they're delivered |
| `scanServiceReportsScansToTheBackend` | **End to end:** a real scan produces "started" and "completed" log entries in the backend |

## 3. UI test plan (performed on the running application)

These were run by driving the real FileGuard window through its actual buttons, using the demo data from `demo\setup-demo.ps1`. A screenshot was checked after every step.

| # | Steps | Expected | Actual | Result |
|---|---|---|---|---|
| U1 | Start the app | Window opens; sidebar shows *Backend online* | As expected | Pass |
| U2 | Backup verification: `Project` vs `Project-Backup` | 13 matching, 1 missing, 1 different, 1 only in backup | 13 / 1 / 1 / 1 | Pass |
| U3 | Duplicate files: `Project` | 3 groups, 3 redundant copies, 125.4 KB | 3 / 3 / 125.4 KB | Pass |
| U4 | File integrity: Save baseline on `Project` | 15 files saved | 15 files (308.3 KB) | Pass |
| U5 | Run `simulate-changes.ps1`, then Run integrity check | 2 added, 2 modified, 1 deleted, 12 unchanged; `Utils.java` = "same size, different hash" | As expected | Pass |
| U6 | Save baseline on `Large-Folder` (6 GB), press **Cancel** at ~5% | "Scan cancelled"; no baseline saved; history shows *Cancelled* | Stopped at 5%; no baseline; Cancelled entry | Pass |
| U7 | Switch pages while a scan runs | Window stays responsive; scan continues | *To check during rehearsal* | — |
| U8 | Scan history: select the integrity check | Stored results load (in the background) | 5 rows shown | Pass |
| U9 | Export CSV from a result | File with header block, rows and disclaimer | File contents verified (same export code the button uses; the save dialog itself is checked by hand) | Pass |
| U10 | Close and reopen the app | All scans and baselines still listed | 5 scans, 1 baseline after restart | Pass |
| U11 | Activity log page | Every scan's started/completed/cancelled events; counts per level | 11 entries, 1 warning | Pass |
| U12 | Open `http://127.0.0.1:8765/` | Log viewer page and JSON API respond | Page returned HTTP 200; `/api/logs` returned the entries | Pass |
| U13 | Settings → Dark, then Light | Both themes apply on every page | Verified on every page in both themes; *remembering the choice after restart is checked during rehearsal* | Pass |
| U14 | Start a second backend while one is running | Refuses with "already running" message | Verified with the backend entry point that `FileGuard Backend.exe` runs | Pass |
| U15 | Choose the same folder for original and backup | Clear error, no scan | Verified by automated test `backupVerificationRejectsSameOrNestedFolders` | Pass |
| U16 | Packaged `FileGuard.exe` | Starts without installed Java; backend answers on port 8765 | Health check returned `fileguard-backend 1.1.0` | Pass |

## 4. Validation (input and error handling)

| Input / situation | Validation |
|---|---|
| Empty or invalid folder path | "Please choose a folder first" / "Folder not found" before any work starts |
| Same folder twice, or nested folders, for backup | Rejected with an explanation |
| Integrity check without a baseline | "Create a baseline first" |
| Unreadable files during a scan | Listed as *Skipped* with the reason; scan continues |
| Replacing an existing baseline | Confirmation dialog |
| Clearing the activity log | Confirmation dialog (permanent) |
| Backend request fields | Level must be INFO/WARN/ERROR; category `[A-Z0-9_]`, max 32; message max 500; details max 4000; body max 16 KB; JSON depth max 32 |
| CSV export | Cell values are quoted and formula characters neutralised |
| SQL | Every query is a `PreparedStatement`; no string concatenation of user data |

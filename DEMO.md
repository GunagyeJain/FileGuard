# FileGuard demo guide

A step-by-step script for showing FileGuard to a teacher or examiner. It covers every feature in about **12–14 minutes** and uses prepared demo folders, so the results are always the same.

The expected numbers below come from actually running these steps.

---

## Before the demo (do this 10 minutes early)

1. **Build the demo folders.** Open PowerShell in the `FileGuard` folder and run:

   ```powershell
   powershell -ExecutionPolicy Bypass -File demo\setup-demo.ps1
   ```

   This creates `FileGuard\demo-data\` (about 6 GB, mostly one big file for the Cancel demo) in a few seconds. Run it again at any time to reset everything.

2. **Optional, for a clean dashboard:** close FileGuard and rename `C:\Users\<you>\.fileguard\fileguard.db` (for example to `fileguard-old.db`). FileGuard creates a new empty database on the next start. Rename it back afterwards to keep your old history.

3. **Start the app:** double-click `dist\FileGuard\FileGuard.exe`. If `dist` doesn't exist yet, double-click `packaging\build-windows-app.bat` once first.

   Check the bottom of the sidebar: it should say **Backend online**.

4. **Keep a File Explorer window open** on `FileGuard\demo-data` so you can show the folders and open files when asked.

### What's in `demo-data`

| Folder | Contents | Used for |
|---|---|---|
| `Project` | 15 files: a report, sample code, notes, a budget, 3 real images, and 3 duplicate copies | Integrity check, duplicate search |
| `Project-Backup` | A copy of `Project` with **1 missing**, **1 changed** and **1 extra** file | Backup verification |
| `Large-Folder` | One 6 GB file | Showing progress and Cancel |

---

## The demo

### 1. Introduction (1 min) — Dashboard

**Say:** "FileGuard is a desktop tool that checks whether files have changed, whether a backup is complete, and which files are duplicates. It compares file contents using SHA-256 hashes and saves every scan in a local SQLite database."

**Show:** the sidebar, the summary cards and the quick actions. Point out the note at the bottom: FileGuard reports *differences*, not corruption.

---

### 2. Backup verification (2 min)

1. Click **Backup verification**.
2. **Original:** `demo-data\Project`. **Backup:** `demo-data\Project-Backup`.
3. Click **Verify backup**.

**Expected:** **13 matching, 1 missing, 1 different, 1 only in backup**

| Status | File | Details |
|---|---|---|
| Missing in backup | `report/results.csv` | Not found in backup folder |
| Content differs | `budget.csv` | **Same size but different content** (one number changed: 400 → 999) |
| Only in backup | `old-version.txt` | Exists only in backup folder |

**Say:** "Files are matched by their path *relative to each folder*, so `Project\budget.csv` is compared with `Project-Backup\budget.csv`. If the sizes differ we know straight away; if they're equal we compare hashes. That's why only files with the same size need to be read fully."

**Bonus:** try choosing the same folder twice, or the backup *inside* the original. FileGuard refuses with a clear message.

---

### 3. Duplicate files (1–2 min)

1. Click **Duplicate files**.
2. Choose `demo-data\Project` and click **Find duplicates**.

**Expected:** **3 duplicate groups, 3 redundant copies, 112.4 KB redundant**
- `photos/dashboard.png` = `downloads/dashboard (1).png`
- `photos/history.png` = `downloads/history-copy.png`
- `notes/todo.txt` = `notes/todo-copy.txt`

**Say:** "First we group files by size, because files of different sizes can't be identical. Only those candidates are hashed, then grouped by hash. Redundant storage is the space we'd free by keeping one copy of each group."

**Show:** open the two `dashboard` images to prove they're the same picture with different names.

---

### 4. File integrity: save a baseline (1 min)

1. Click **File integrity**.
2. **Browse** → choose `demo-data\Project`.
3. Click **Save baseline**.

**Expected:** "Baseline saved with 15 files (278.9 KB)". The line under the folder now shows the baseline date and file count.

**Say:** "A baseline is a snapshot: for every file we store its relative path, size, modification time and SHA-256 hash in the database."

---

### 5. Change some files (1 min)

Run in PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File demo\simulate-changes.ps1
```

**Or, more convincing:** make the changes by hand while the teacher watches:
- open `Project\code\Utils.java` in Notepad, change `a + b` to `a - b`, and save
- delete `Project\notes\meeting-notes.txt`
- create a new text file in `Project\report\`

(If you change files by hand, the numbers will differ from the ones below.)

---

### 6. File integrity: run the check (1–2 min)

Click **Run integrity check**.

**Expected (with the script):** **2 added, 2 modified, 1 deleted, 12 unchanged**

| Status | File | Details |
|---|---|---|
| Added | `notes/viva-questions.txt` | New file since baseline |
| Added | `report/appendix.txt` | New file since baseline |
| Modified | `code/Utils.java` | **Same size, different hash** |
| Modified | `report/final-report.txt` | Size 432 B → 478 B |
| Deleted | `notes/meeting-notes.txt` | File no longer present |

**Say (important):** "`Utils.java` has exactly the same size as before. Only one character changed, `+` to `-`. Comparing sizes or dates would miss this; comparing SHA-256 hashes catches it. And we only say *modified*: FileGuard can't know whether the change was intended."

---

### 7. Export a report (1 min)

Click **Export CSV**, save it, and open it in Excel or Notepad.

**Show:** the header block (scan type, folder, date, summary, disclaimer) followed by one row per finding.

---

### 8. Background scanning and Cancel (1 min)

1. Go to **File integrity**, choose `demo-data\Large-Folder`, and click **Save baseline**.
2. While the progress bar moves, **switch to another page and back**: the window stays responsive.
3. Press **Cancel** before it reaches 100 %.

**Expected:** "Scan cancelled". No baseline is saved for `Large-Folder`.

**Say:** "Scans run on a background thread using a JavaFX `Task`, so the window never freezes. The file is read in 64 KB chunks, so even a 6 GB file never has to fit in memory. After each chunk we check whether Cancel was pressed, so it stops almost immediately and saves nothing half-finished."

> The 6 GB file takes about 6 seconds on a fast SSD. Press Cancel within the first 2–3 seconds. If it finishes too quickly on your laptop, recreate the data with a bigger file:
> `powershell -ExecutionPolicy Bypass -File demo\setup-demo.ps1 -LargeFileMB 12288`

---

### 9. Scan history and persistence (1–2 min)

1. Click **Scan history**. Every scan appears, including the **Cancelled** one.
2. Click any row to see its stored results, and export it again if you like.
3. **Close FileGuard completely and start it again.** Open Scan history: everything is still there, and the dashboard cards show the saved baselines and latest results.

**Say:** "Everything is stored in SQLite in `.fileguard\fileguard.db` in the user's home folder. The Settings page shows the exact path. The database and tables are created automatically on the first run."

---

### 10. Local backend and activity log (1–2 min)

1. Click **Activity log**. Every scan from this demo appears: started, completed and cancelled events, with counts per level at the top.
2. Click **Open in browser**. The same log, served by the backend at `http://127.0.0.1:8765/`, refreshes every 5 seconds.
3. Optional: open `http://127.0.0.1:8765/api/logs` in the browser to show the raw JSON from the REST API.

**Say:** "The backend is a small REST server built only from parts of Java: the JDK's built-in HTTP server, no framework. It stores the activity log in its own SQLite database, separate from the app's data, just as a real server keeps its own storage. The app sends logs with Java's HTTP client in the background, so the UI never waits. If the backend is down, logs are queued and delivered later."

**Bonus, to show it's a separate process:** close FileGuard, double-click `dist\FileGuard\FileGuard Backend.exe` (a console window opens), then start `FileGuard.exe` again. The app detects the running backend and uses it instead of starting its own. Close the console window and the sidebar switches to **Backend offline** within a few seconds.

---

### 11. Settings (30 s)

Switch between **Light** and **Dark** theme. Restart the app to show that the choice is remembered.

---

## Likely viva questions

| Question | Short answer |
|---|---|
| Why SHA-256? | A cryptographic hash: any change to the content gives a completely different hash, and two different files having the same hash is practically impossible. It's built into Java (`MessageDigest`). |
| Why not compare dates or sizes? | They can stay the same when the content changes (see `Utils.java` in the demo). |
| How do you handle large files? | They're streamed in 64 KB chunks (`FileHasher`), so memory use stays the same for any file size. |
| How does Cancel work? | `ScanTask` extends JavaFX `Task`. The scanning code calls `checkCancelled()` after each chunk and stops with a `CancellationException`; a "Cancelled" entry goes into the history and nothing else is saved. |
| Why doesn't the UI freeze? | Scans run on a separate daemon thread. JavaFX `Task` passes progress and messages safely back to the UI thread. |
| What if a file can't be read? | It's listed as **Skipped** with the reason (for example access denied) and the scan carries on. A skipped file isn't reported as deleted. |
| How is the code organised? | `model` (data), `core` (files and hashing, no UI or SQL), `db` (JDBC DAOs), `service` (runs a scan and saves it), `ui` (JavaFX). |
| How do you avoid SQL injection? | Every query uses `PreparedStatement`. Connections are closed with try-with-resources, and saves run in one transaction. |
| Is a "modified" file corrupted? | Not necessarily. FileGuard only shows that the content is different; a person has to decide whether the change was intended. |
| What is the backend? | A local REST server (`BackendServer`) on `127.0.0.1:8765`, built with the JDK's `com.sun.net.httpserver`. Endpoints: `GET/POST/DELETE /api/logs`, `GET /api/stats`, `GET /api/health`. It stores logs in `backend.db` through `LogRepository` (JDBC, prepared statements). |
| How does the app talk to it? | `BackendClient` uses `java.net.http.HttpClient` to send JSON asynchronously. A health check every 5 seconds updates the online/offline status and flushes queued logs. |
| Is the backend secure? | It only listens on 127.0.0.1 (not reachable from the network), validates every field, limits request size to 16 KB, and returns generic error messages without internal details. |
| What did you test? | 30 JUnit tests: SHA-256 known values, large files, added/modified/deleted detection, backup and duplicate logic, cancellation, data surviving a restart, CSV export, the JSON parser, every REST endpoint (including invalid input), and the client's offline queue. Run them with `mvnw test`. |

---

## If something goes wrong

| Problem | Fix |
|---|---|
| "running scripts is disabled" | Use the full command shown above, which includes `-ExecutionPolicy Bypass`. |
| Numbers don't match this guide | Run `demo\setup-demo.ps1` again to reset the demo folders. |
| Integrity check says there's no baseline | Save a baseline for that exact folder first (step 4). |
| Sidebar says **Backend offline** | Another program is using port 8765. Close it, or carry on: logs are queued and sent later. |
| `FileGuard.exe` is missing | Double-click `packaging\build-windows-app.bat` (needs JDK 21 installed). |
| Big-file scan finishes before you can cancel | Recreate the data with `-LargeFileMB 12288`. |

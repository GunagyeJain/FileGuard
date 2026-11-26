# FileGuard demo setup
# Creates FileGuard\demo-data with folders that show every feature:
#   Project         - the "original" folder (text files, images, a few duplicates)
#   Project-Backup  - a copy of Project with 1 missing, 1 different and 1 extra file
#   Large-Folder    - one big file, so a scan runs long enough to press Cancel
#
# Run from the FileGuard folder:
#   powershell -ExecutionPolicy Bypass -File demo\setup-demo.ps1
# Optional: -LargeFileMB 10240 if the scan finishes before you can press Cancel,
#           -LargeFileMB 2048 to save disk space on a slow machine.

param([int]$LargeFileMB = 6144)

$ErrorActionPreference = 'Stop'
$root    = Split-Path -Parent $PSScriptRoot
$demo    = Join-Path $root 'demo-data'
$project = Join-Path $demo 'Project'
$backup  = Join-Path $demo 'Project-Backup'
$large   = Join-Path $demo 'Large-Folder'
$images  = Join-Path $PSScriptRoot 'sample-images'

function Write-DemoFile([string]$base, [string]$relative, [string]$text) {
    $path = Join-Path $base $relative
    New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
    [IO.File]::WriteAllText($path, $text)
}

# Start fresh (only ever deletes FileGuard\demo-data).
if (Test-Path -LiteralPath $demo) { Remove-Item -LiteralPath $demo -Recurse -Force }
Write-Host "Creating demo data in $demo"

# ---------------------------------------------------------------- Project
Write-DemoFile $project 'report\final-report.txt' @"
FileGuard - Final Project Report
================================
1. Introduction
FileGuard checks file integrity, verifies backups and finds duplicate files
using SHA-256 hashes. All data is stored locally in SQLite.

2. Objectives
- Detect added, modified and deleted files compared to a saved baseline.
- Confirm that a backup folder contains identical copies of every file.
- Identify duplicate files and the storage they waste.
"@
Write-DemoFile $project 'report\references.txt' @"
[1] NIST FIPS 180-4, Secure Hash Standard (SHA-256).
[2] OpenJFX documentation, https://openjfx.io
[3] SQLite documentation, https://sqlite.org/docs.html
"@
Write-DemoFile $project 'report\results.csv' @"
test,files,duration_ms,result
integrity,120,850,pass
backup,240,1310,pass
duplicates,120,640,pass
"@
Write-DemoFile $project 'code\Main.java' @"
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from the demo project");
    }
}
"@
Write-DemoFile $project 'code\Utils.java' @"
public final class Utils {
    private Utils() { }

    public static int add(int a, int b) {
        return a + b;
    }
}
"@
Write-DemoFile $project 'code\README.md' @"
# Demo code
Small sample sources used to demonstrate FileGuard.
"@
Write-DemoFile $project 'notes\meeting-notes.txt' @"
Team meeting - week 10
- Finish backup verification screen
- Prepare demo for the teacher
- Write README and screenshots
"@
Write-DemoFile $project 'notes\todo.txt' @"
TODO
[x] Hashing
[x] Database
[ ] Practise the demo
"@
Write-DemoFile $project 'budget.csv' @"
item,cost
printing,250
snacks,400
"@

# Real images (fixed copies in demo\sample-images) so they can be opened during the demo.
New-Item -ItemType Directory -Force -Path (Join-Path $project 'photos') | Out-Null
Copy-Item (Join-Path $images 'dashboard.png')       (Join-Path $project 'photos\dashboard.png')
Copy-Item (Join-Path $images 'history.png')         (Join-Path $project 'photos\history.png')
Copy-Item (Join-Path $images 'backup.png')          (Join-Path $project 'photos\backup.png')

# Duplicates: 3 groups (2 images + 1 text file).
New-Item -ItemType Directory -Force -Path (Join-Path $project 'downloads') | Out-Null
Copy-Item (Join-Path $project 'photos\dashboard.png') (Join-Path $project 'downloads\dashboard (1).png')
Copy-Item (Join-Path $project 'photos\history.png')   (Join-Path $project 'downloads\history-copy.png')
Copy-Item (Join-Path $project 'notes\todo.txt')       (Join-Path $project 'notes\todo-copy.txt')

# ---------------------------------------------------------------- Backup
Copy-Item -LiteralPath $project -Destination $backup -Recurse
Remove-Item -LiteralPath (Join-Path $backup 'report\results.csv')          # missing in backup
Write-DemoFile $backup 'budget.csv' @"
item,cost
printing,250
snacks,999
"@                                                                          # same size, different content
Write-DemoFile $backup 'old-version.txt' "An old file that only exists in the backup."   # only in backup

# ---------------------------------------------------------------- Large file (for Cancel)
New-Item -ItemType Directory -Force -Path $large | Out-Null
$bigFile = Join-Path $large 'big-video.bin'
Write-Host "Writing a $LargeFileMB MB file for the cancellation demo (this takes a moment)..."
$buffer = New-Object byte[] (4MB)
(New-Object Random 7).NextBytes($buffer)
$stream = [IO.File]::Create($bigFile)
try {
    for ($i = 0; $i -lt ($LargeFileMB / 4); $i++) {
        $buffer[0] = [byte]($i % 256)   # make every block different
        $stream.Write($buffer, 0, $buffer.Length)
    }
} finally {
    $stream.Dispose()
}

Write-Host ""
Write-Host "Done. Use these folders in FileGuard:"
Write-Host "  Integrity / duplicates : $project"
Write-Host "  Backup verification    : $project  ->  $backup"
Write-Host "  Cancellation demo      : $large"

# FileGuard demo: change the Project folder AFTER saving a baseline.
# Makes 1 modified, 1 deleted and 2 added files so the integrity check has something to find.
#
# Run from the FileGuard folder:
#   powershell -ExecutionPolicy Bypass -File demo\simulate-changes.ps1

$ErrorActionPreference = 'Stop'
$project = Join-Path (Split-Path -Parent $PSScriptRoot) 'demo-data\Project'
if (-not (Test-Path -LiteralPath $project)) {
    throw "Demo folder not found. Run demo\setup-demo.ps1 first."
}

# Modified: the report gets a new section.
Add-Content -LiteralPath (Join-Path $project 'report\final-report.txt') -Value @"

3. Conclusion
FileGuard met all objectives.
"@

# Modified: a single character changes (same size, different hash).
$utils = Join-Path $project 'code\Utils.java'
[IO.File]::WriteAllText($utils, ([IO.File]::ReadAllText($utils) -replace 'a \+ b', 'a - b'))

# Deleted
Remove-Item -LiteralPath (Join-Path $project 'notes\meeting-notes.txt')

# Added
[IO.File]::WriteAllText((Join-Path $project 'notes\viva-questions.txt'), "Why SHA-256? Why stream files?")
[IO.File]::WriteAllText((Join-Path $project 'report\appendix.txt'), "Appendix A - test data")

Write-Host "Changes made in $project :"
Write-Host "  modified : report\final-report.txt, code\Utils.java"
Write-Host "  deleted  : notes\meeting-notes.txt"
Write-Host "  added    : notes\viva-questions.txt, report\appendix.txt"

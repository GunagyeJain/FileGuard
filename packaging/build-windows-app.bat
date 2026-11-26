@echo off
rem ---------------------------------------------------------------------------
rem  Builds dist\FileGuard\FileGuard.exe  (double-click this file to run it)
rem
rem  The result is a normal Windows program folder with its own Java runtime
rem  inside, so FileGuard runs on any Windows PC without installing Java.
rem  Requirement for BUILDING only: JDK 21 (it includes the jpackage tool).
rem ---------------------------------------------------------------------------
setlocal
set VERSION=1.1.0
cd /d "%~dp0.."

set JPACKAGE=jpackage
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set JPACKAGE="%JAVA_HOME%\bin\jpackage.exe"

echo [1/3] Compiling and testing FileGuard...
rem Full path on purpose: some PCs block running programs from the current folder by name.
call "%CD%\mvnw.cmd" -q -B clean package
if errorlevel 1 goto :failed

echo [2/3] Creating the Windows application (this takes about a minute)...
if exist "dist\FileGuard" rmdir /s /q "dist\FileGuard"
%JPACKAGE% --type app-image ^
  --name FileGuard ^
  --app-version %VERSION% ^
  --vendor "FileGuard" ^
  --description "File integrity, backup verification and duplicate detection" ^
  --icon packaging\fileguard.ico ^
  --input target\app ^
  --main-jar fileguard-%VERSION%.jar ^
  --main-class com.fileguard.Launcher ^
  --add-launcher "FileGuard Backend=packaging\backend-launcher.properties" ^
  --add-modules java.base,java.desktop,java.logging,java.naming,java.net.http,java.prefs,java.scripting,java.sql,java.xml,jdk.httpserver,jdk.unsupported,jdk.crypto.ec,jdk.charsets,jdk.localedata ^
  --jlink-options "--strip-debug --no-man-pages --no-header-files" ^
  --dest dist
if errorlevel 1 goto :failed

echo [3/3] Creating a zip to share...
if exist "dist\FileGuard-%VERSION%-windows.zip" del "dist\FileGuard-%VERSION%-windows.zip"
powershell -NoProfile -Command "Compress-Archive -Path 'dist\FileGuard' -DestinationPath 'dist\FileGuard-%VERSION%-windows.zip'"

echo.
echo Done!
echo   App : %CD%\dist\FileGuard\FileGuard.exe
echo   Zip : %CD%\dist\FileGuard-%VERSION%-windows.zip
echo.
pause
exit /b 0

:failed
echo.
echo Build failed - see the messages above.
pause
exit /b 1

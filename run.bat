@echo off
cd /d "%~dp0"
if not exist out mkdir out
if not exist lib mkdir lib
javac -d out src\sudar\*.java
if errorlevel 1 exit /b 1
if /i "%1"=="api" (
  java -cp "out;lib/*" sudar.Main --api %2
) else (
  java -cp "out;lib/*" sudar.Main %*
)

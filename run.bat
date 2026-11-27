@echo off
cd /d "%~dp0"
if not exist out mkdir out
javac -cp "lib/*" -d out *.java || exit /b 1
java --enable-native-access=ALL-UNNAMED -cp "out;lib/*" Main %*

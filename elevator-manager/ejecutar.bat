@echo off
rem Ejecuta el programa. Argumentos opcionales: elevadores pisos msEntrePisos msParada
cd /d "%~dp0"
if not exist out call compilar.bat
java -cp out manager.ElevatorManager %*

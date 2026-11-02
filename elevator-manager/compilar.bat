@echo off
rem Compila todo el codigo fuente a la carpeta out\
cd /d "%~dp0"
if exist out rmdir /s /q out
dir /s /b src\*.java > fuentes.txt
javac -encoding UTF-8 -d out @fuentes.txt
del fuentes.txt
echo Compilado en .\out

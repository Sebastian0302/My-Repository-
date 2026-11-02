#!/usr/bin/env bash
# Compila todo el codigo fuente a la carpeta out/
set -e
cd "$(dirname "$0")"
rm -rf out
javac -encoding UTF-8 -d out $(find src -name "*.java")
echo "Compilado en ./out"

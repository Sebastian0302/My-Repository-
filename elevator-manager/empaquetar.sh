#!/usr/bin/env bash
# Crea el .zip de entrega: ./empaquetar.sh 10A  ->  pj1-grupo10A.zip
cd "$(dirname "$0")"
GRUPO="${1:?Uso: ./empaquetar.sh <numeroDeGrupo>}"
rm -f "pj1-grupo${GRUPO}.zip"
zip -r "pj1-grupo${GRUPO}.zip" src ejemplos docs README.md compilar.sh compilar.bat ejecutar.sh ejecutar.bat
echo "Creado pj1-grupo${GRUPO}.zip"

#!/usr/bin/env bash
# Ejecuta el programa. Argumentos opcionales: elevadores pisos msEntrePisos msParada
cd "$(dirname "$0")"
[ -d out ] || ./compilar.sh
java -cp out manager.ElevatorManager "$@"

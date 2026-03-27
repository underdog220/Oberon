#!/usr/bin/env bash
# Oberon Server — Linux/macOS Start-Script
# Konfiguration ueber Umgebungsvariablen (siehe README)

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/build/libs/oberon-all.jar"

export OBERON_TOKEN="${OBERON_TOKEN:-oberon-dev-token}"
export OBERON_PORT="${OBERON_PORT:-17900}"

echo "=== Oberon Server ==="
echo "Port: $OBERON_PORT"
echo "JAR: $JAR"
echo ""

exec java -jar "$JAR"

#!/usr/bin/env bash
# run.sh — start any backend service with the .env auto-loaded.
#
# Usage:
#   ./run.sh user-services
#   ./run.sh config-server
#
# This replaces the two-step "source ./load-env.sh + ./mvnw spring-boot:run".
# It loads .env for you, every time, automatically.

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
SERVICE="$1"

SERVICES="config-server api-gateway user-services profile-service rides-service notification-service messaging-service"

if [ -z "$SERVICE" ]; then
  echo "Usage: ./run.sh <service>"
  echo
  echo "Available services:"
  for s in $SERVICES; do echo "   ./run.sh $s"; done
  echo
  echo "Tip: start config-server FIRST, then the others."
  exit 1
fi

if [ ! -d "$DIR/$SERVICE" ]; then
  echo "ERROR: no service folder named '$SERVICE'."
  echo "Available: $SERVICES"
  exit 1
fi

# --- auto-load .env into this run ---
if [ -f "$DIR/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$DIR/.env"
  set +a
  echo "Loaded .env  (DB_HOST=$DB_HOST)"
else
  echo "WARNING: no .env file found at $DIR/.env — using built-in defaults (localhost)."
fi

echo "Starting '$SERVICE' ..."
echo
cd "$DIR/$SERVICE" && exec ./mvnw spring-boot:run

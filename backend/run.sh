#!/usr/bin/env bash
# run.sh — start the SafeRide backend (ride-sharing-backend) with .env auto-loaded.
#
# Usage:
#   ./run.sh
#
# The old microservices were moved to /Users/novaratech/Documents/projects/microservices
# (they have their own run.sh there).

set -e
DIR="$(cd "$(dirname "$0")" && pwd)"

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

echo "Starting ride-sharing-backend ..."
echo
cd "$DIR/ride-sharing-backend" && exec ./mvnw spring-boot:run

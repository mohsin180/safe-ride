# load-env.sh — macOS/Linux equivalent of load-env.ps1.
# Loads a .env file into the CURRENT shell so the Spring services (which do
# NOT read .env themselves) pick the values up as environment variables.
#
# IMPORTANT: SOURCE it (don't execute) so the vars persist in your shell:
#     source ./load-env.sh            # loads ./.env
#     source ./load-env.sh .env.prod  # loads a specific file
# Then start a service from the SAME shell:
#     cd user-services && ./mvnw spring-boot:run

# Resolve directory of this script so it works from any service folder.
_ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-${(%):-%x}}")" && pwd)"
_ENV_FILE="${1:-$_ENV_DIR/.env}"
case "$_ENV_FILE" in /*) ;; *) _ENV_FILE="$_ENV_DIR/$_ENV_FILE" ;; esac

if [ ! -f "$_ENV_FILE" ]; then
  echo "No env file at '$_ENV_FILE'."
  echo "Create it first:  cp '$_ENV_DIR/.env.example' '$_ENV_DIR/.env'"
  return 1 2>/dev/null || exit 1
fi

set -a                 # auto-export everything that gets assigned
# shellcheck disable=SC1090
. "$_ENV_FILE"
set +a

echo "Loaded environment variables from '$_ENV_FILE' into this session."

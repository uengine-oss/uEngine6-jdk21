#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sql_file="$script_dir/seed-dashboard-dummy-data.sql"
mode="${1:-seed}"

case "$mode" in
  seed)
    cleanup=false
    ;;
  cleanup|--cleanup)
    cleanup=true
    ;;
  -h|--help)
    cat <<'USAGE'
Usage: seed-dashboard-dummy-data.sh [seed|cleanup]

Environment variables:
  POSTGRES_HOST                       default: localhost
  POSTGRES_PORT                       default: 5432
  POSTGRES_DB                         default: uengine
  POSTGRES_USER                       default: uengine
  POSTGRES_PASSWORD                   default: uengine
  UENGINE_DB_SCHEMA                   default: public
  UENGINE_ANALYTICS_ETL_TIME_ZONE     default: Asia/Seoul
  ANALYTICS_DUMMY_DAYS                default: 90
  ANALYTICS_DUMMY_PROCESSES_PER_DAY   default: 20
  ANALYTICS_DUMMY_TASKS_PER_PROCESS   default: 6
  ANALYTICS_DUMMY_END_DATE            default: database current date
  ANALYTICS_DUMMY_RANDOM_SEED         default: 0.4242 (-1 through 1)
  PSQL_BIN                            default: psql
USAGE
    exit 0
    ;;
  *)
    echo "Unknown mode: $mode (expected seed or cleanup)" >&2
    exit 2
    ;;
esac

psql_bin="${PSQL_BIN:-psql}"
if ! command -v "$psql_bin" >/dev/null 2>&1; then
  echo "psql was not found. Install the PostgreSQL client or set PSQL_BIN." >&2
  exit 127
fi

postgres_host="${POSTGRES_HOST:-localhost}"
postgres_port="${POSTGRES_PORT:-5432}"
postgres_db="${POSTGRES_DB:-uengine}"
postgres_user="${POSTGRES_USER:-uengine}"
export PGPASSWORD="${POSTGRES_PASSWORD:-uengine}"

psql_args=(
  -X
  --host "$postgres_host"
  --port "$postgres_port"
  --dbname "$postgres_db"
  --username "$postgres_user"
  --set ON_ERROR_STOP=1
  --set "schema=${UENGINE_DB_SCHEMA:-public}"
  --set "dummy_days=${ANALYTICS_DUMMY_DAYS:-90}"
  --set "processes_per_day=${ANALYTICS_DUMMY_PROCESSES_PER_DAY:-20}"
  --set "tasks_per_process=${ANALYTICS_DUMMY_TASKS_PER_PROCESS:-6}"
  --set "time_zone=${UENGINE_ANALYTICS_ETL_TIME_ZONE:-Asia/Seoul}"
  --set "random_seed=${ANALYTICS_DUMMY_RANDOM_SEED:-0.4242}"
  --set "cleanup=$cleanup"
)

if [[ -n "${ANALYTICS_DUMMY_END_DATE:-}" ]]; then
  psql_args+=(--set "end_date=$ANALYTICS_DUMMY_END_DATE")
fi

exec "$psql_bin" "${psql_args[@]}" --file "$sql_file"

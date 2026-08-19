#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
KEYCLOAK_HOME="${KEYCLOAK_HOME:-$SCRIPT_DIR/keycloak-26.6.1}"

export KC_BOOTSTRAP_ADMIN_USERNAME="${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}"
export KC_BOOTSTRAP_ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-admin}"
export KEYCLOAK_HTTP_PORT="${KEYCLOAK_HTTP_PORT:-8080}"

cd "$KEYCLOAK_HOME"
exec bin/kc.sh start-dev --http-port="$KEYCLOAK_HTTP_PORT" --import-realm


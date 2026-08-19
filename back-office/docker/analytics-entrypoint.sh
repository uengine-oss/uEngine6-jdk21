#!/bin/sh
set -eu

cat > /usr/share/nginx/html/admin/static/env.txt <<EOF
PROFILE=${PROFILE:-dev}
CONFIG_JSON=${CONFIG_JSON:-{"vcap":{"services":{"uengine5-router":{"dev":{"external":"localhost:8088"}}}}}}
ANALYTICS_API_URL=${ANALYTICS_API_URL:-/analytics-api}
EOF

exec nginx -g 'daemon off;'

#!/bin/sh
set -eu

: "${RENDER_EXTERNAL_URL:?Set RENDER_EXTERNAL_URL to the public HTTPS URL}"
export PUBLIC_BASE_URL="$RENDER_EXTERNAL_URL"
export WEB_ORIGINS="${WEB_ORIGINS:-$RENDER_EXTERNAL_URL}"
export PORT="${PORT:-10000}"
case "$PORT" in ''|*[!0-9]*) echo 'PORT must be numeric' >&2; exit 1;; esac

envsubst '${PORT}' < /app/nginx.conf.template > /tmp/samuschat-nginx.conf
nginx -t -c /tmp/samuschat-nginx.conf
java -jar /app/app.jar &
api_pid=$!
nginx -c /tmp/samuschat-nginx.conf -g 'daemon off;' &
proxy_pid=$!
cleanup() {
    trap - EXIT INT TERM
    kill -TERM "$api_pid" "$proxy_pid" 2>/dev/null || true
    wait "$api_pid" 2>/dev/null || true
    wait "$proxy_pid" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# Both processes belong to the same service; failure of either stops the other.
wait -n "$api_pid" "$proxy_pid"
exit 1

#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

compose=(docker compose --project-name samuschat --env-file .env.prod -f docker-compose.prod.yml)
"${compose[@]}" config --quiet
"${compose[@]}" pull
"${compose[@]}" up -d --wait --wait-timeout 180 postgres redis-master redis-replica

# Stop at the first unhealthy replacement, leaving the remaining instances intact.
# Existing WebSocket connections on a replaced instance must reconnect.
for backend in backend1 backend2 backend3; do
    echo "Deploying $backend"
    "${compose[@]}" up -d --no-deps --wait --wait-timeout 180 "$backend"
    "${compose[@]}" exec -T "$backend" wget -q -O /dev/null \
        http://localhost:8080/actuator/health/readiness
done

"${compose[@]}" up -d --no-deps --wait --wait-timeout 60 nginx
"${compose[@]}" exec -T nginx nginx -t
"${compose[@]}" exec -T nginx wget -q -O /dev/null http://127.0.0.1/health
echo 'Deploy complete: all three backends and Nginx are healthy.'

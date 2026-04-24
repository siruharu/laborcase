#!/usr/bin/env bash
# Start / stop the local pgvector container used by Flyway + JPA tests.
# Chosen over Testcontainers because docker-java 3.4 (bundled with
# Testcontainers 1.21.3) cannot negotiate with Docker Desktop 29 — the
# /info endpoint returns a Status 400 placeholder. See
# docs/research/docker-testcontainers-29.md for the investigation.
set -euo pipefail

NAME="${PG_TEST_NAME:-pgvector-test}"
PORT="${PG_TEST_PORT:-54320}"
IMAGE="pgvector/pgvector:pg16"

cmd="${1:-up}"

case "$cmd" in
  up)
    if docker ps --format '{{.Names}}' | grep -q "^${NAME}$"; then
      echo "✓ ${NAME} already running on port ${PORT}"
      exit 0
    fi
    docker rm -f "${NAME}" >/dev/null 2>&1 || true
    docker run -d \
      --name "${NAME}" \
      -p "${PORT}:5432" \
      -e POSTGRES_USER=test \
      -e POSTGRES_PASSWORD=test \
      -e POSTGRES_DB=laborcase_test \
      "${IMAGE}" >/dev/null

    echo -n "waiting for pg_isready"
    for _ in $(seq 1 30); do
      if docker exec "${NAME}" pg_isready -U test >/dev/null 2>&1; then
        echo " ✓"
        echo "  JDBC URL: jdbc:postgresql://localhost:${PORT}/laborcase_test"
        echo "  user=test password=test"
        exit 0
      fi
      echo -n "."
      sleep 1
    done
    echo " ✗ timed out"
    exit 1
    ;;
  down)
    docker rm -f "${NAME}" >/dev/null 2>&1 && echo "✓ ${NAME} removed" || echo "⚠ ${NAME} not running"
    ;;
  logs)
    docker logs "${NAME}"
    ;;
  psql)
    docker exec -it "${NAME}" psql -U test -d laborcase_test
    ;;
  *)
    echo "usage: $0 {up|down|logs|psql}" >&2
    exit 2
    ;;
esac

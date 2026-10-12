#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

for tool in java node npm docker curl openssl; do
	if ! command -v "$tool" >/dev/null 2>&1; then
		echo "Falta o comando ${tool}." >&2
		exit 1
	fi
done

if [[ ! -f .env ]]; then
	password="$(openssl rand -hex 24)"
	umask 077
	cat >.env <<EOF
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/skillforge
SPRING_DATASOURCE_USERNAME=skillforge
SPRING_DATASOURCE_PASSWORD=${password}
POSTGRES_DB=skillforge
POSTGRES_USER=skillforge
POSTGRES_PASSWORD=${password}
API_PORT=8080
WEB_PORT=3000
API_URL=http://localhost:8080
EOF
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

: "${SPRING_DATASOURCE_URL:?}"
: "${SPRING_DATASOURCE_USERNAME:?}"
: "${SPRING_DATASOURCE_PASSWORD:?}"
: "${POSTGRES_DB:?}"
: "${POSTGRES_USER:?}"
: "${POSTGRES_PASSWORD:?}"
: "${API_PORT:?}"
: "${WEB_PORT:?}"
: "${API_URL:?}"

docker compose up -d --wait

api_pid=""
web_pid=""

# A process that already exited makes kill fail. That is the shutdown we asked for.
stop() {
	local pid="$1"
	if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
		kill "$pid"
	fi
}

cleanup() {
	trap - EXIT INT TERM
	stop "$web_pid"
	stop "$api_pid"
	# The process we just stopped exits non-zero. The script status comes from wait -n.
	if [[ -n "$web_pid" ]]; then
		wait "$web_pid" || true
	fi
	if [[ -n "$api_pid" ]]; then
		wait "$api_pid" || true
	fi
}
trap cleanup EXIT INT TERM

(
	cd api
	SERVER_PORT="$API_PORT" ./mvnw -q spring-boot:run
) &
api_pid="$!"

(
	cd web
	if [[ ! -d node_modules ]]; then
		npm ci
	fi
	API_URL="$API_URL" NEXT_TELEMETRY_DISABLED=1 npx next dev --port "$WEB_PORT"
) &
web_pid="$!"

wait_for() {
	local url="$1"
	local name="$2"
	local pid="$3"
	local attempt=0
	while [[ "$attempt" -lt 90 ]]; do
		attempt=$((attempt + 1))
		if curl -fsS "$url" >/dev/null; then
			return 0
		fi
		if ! kill -0 "$pid" 2>/dev/null; then
			echo "${name} encerrou antes de responder." >&2
			exit 1
		fi
		sleep 1
	done
	echo "${name} não respondeu em ${attempt}s: ${url}" >&2
	exit 1
}

wait_for "${API_URL}/actuator/health/liveness" "A API" "$api_pid"
wait_for "http://localhost:${WEB_PORT}" "A interface" "$web_pid"

echo "Skillforge no ar."
echo "Interface: http://localhost:${WEB_PORT}"
echo "Vida: ${API_URL}/actuator/health/liveness"
echo "Prontidão: ${API_URL}/actuator/health/readiness"

wait -n "$api_pid" "$web_pid"

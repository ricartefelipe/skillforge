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

ensure() {
	local name="$1"
	local value="$2"
	if grep -q "^${name}=" .env; then
		return 0
	fi
	printf '%s=%s\n' "$name" "$value" >> .env
}

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
WEB_URL=http://localhost:3000
KEYCLOAK_ISSUER=http://localhost:8180/realms/skillforge
KEYCLOAK_CLIENT_ID=skillforge
EOF
fi

ensure KEYCLOAK_ADMIN admin
ensure KEYCLOAK_ADMIN_PASSWORD "$(openssl rand -hex 24)"
ensure ALUNO_PASSWORD "$(openssl rand -hex 24)"
ensure DOCENTE_PASSWORD "$(openssl rand -hex 24)"
ensure ADMINISTRACAO_PASSWORD "$(openssl rand -hex 24)"
ensure WEB_URL "http://localhost:3000"
ensure KEYCLOAK_ISSUER "http://localhost:8180/realms/skillforge"
ensure KEYCLOAK_CLIENT_ID skillforge

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
: "${WEB_URL:?}"
: "${KEYCLOAK_ISSUER:?}"
: "${KEYCLOAK_CLIENT_ID:?}"
: "${KEYCLOAK_ADMIN:?}"
: "${KEYCLOAK_ADMIN_PASSWORD:?}"
: "${ALUNO_PASSWORD:?}"
: "${DOCENTE_PASSWORD:?}"
: "${ADMINISTRACAO_PASSWORD:?}"

if curl -fsS "${API_URL}/actuator/health/liveness" >/dev/null \
	&& curl -fsS "${WEB_URL}" >/dev/null \
	&& curl -fsS "${KEYCLOAK_ISSUER}/.well-known/openid-configuration" >/dev/null; then
	echo "Skillforge já está no ar."
	echo "Interface: ${WEB_URL}"
	exit 0
fi

docker compose up -d --wait postgres

database_exists="$(docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = 'keycloak'")"
if [[ "$database_exists" != "1" ]]; then
	docker compose exec -T postgres psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE keycloak"
fi

docker compose up -d --wait keycloak

attempt=0
while [[ "$attempt" -lt 60 ]]; do
	attempt=$((attempt + 1))
	if curl -fsS "${KEYCLOAK_ISSUER}/.well-known/openid-configuration" >/dev/null; then
		break
	fi
	if [[ "$attempt" -eq 60 ]]; then
		echo "O acesso não respondeu a tempo." >&2
		exit 1
	fi
	sleep 2
done

docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh config credentials \
	--server http://localhost:8080 \
	--realm master \
	--user "$KEYCLOAK_ADMIN" \
	--password "$KEYCLOAK_ADMIN_PASSWORD"

# The default profile requires an email and a last name. These accounts only have a first name.
docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh update users/profile \
	-r skillforge \
	-f - < keycloak/user-profile.json

set_password() {
	local username="$1"
	local password="$2"
	docker compose exec -T keycloak /opt/keycloak/bin/kcadm.sh set-password \
		-r skillforge \
		--username "$username" \
		--new-password "$password"
}

set_password aluno "$ALUNO_PASSWORD"
set_password docente "$DOCENTE_PASSWORD"
set_password administracao "$ADMINISTRACAO_PASSWORD"

port_in_use() {
	local port="$1"
	if ! command -v ss >/dev/null 2>&1; then
		return 1
	fi
	ss -ltn "sport = :${port}" | awk 'NR > 1 { found = 1 } END { exit !found }'
}

if curl -fsS "${API_URL}/actuator/health/liveness" >/dev/null && curl -fsS "${WEB_URL}" >/dev/null; then
	echo "Skillforge já está no ar."
	echo "Interface: ${WEB_URL}"
	exit 0
fi

if port_in_use "$API_PORT" || port_in_use "$WEB_PORT"; then
	echo "A porta ${API_PORT} ou ${WEB_PORT} já está em uso. Encerre a instância anterior antes de subir outra." >&2
	exit 1
fi

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
	local try=0
	while [[ "$try" -lt 90 ]]; do
		try=$((try + 1))
		if curl -fsS "$url" >/dev/null; then
			if kill -0 "$pid" 2>/dev/null; then
				return 0
			fi
			echo "${name} encerrou antes de responder." >&2
			exit 1
		fi
		if ! kill -0 "$pid" 2>/dev/null; then
			echo "${name} encerrou antes de responder." >&2
			exit 1
		fi
		sleep 1
	done
	echo "${name} não respondeu em ${try}s: ${url}" >&2
	exit 1
}

wait_for "${API_URL}/actuator/health/liveness" "A API" "$api_pid"
wait_for "${WEB_URL}" "A interface" "$web_pid"

echo "Skillforge no ar."
echo "Interface: ${WEB_URL}"
echo "Contas: aluno, docente, administracao. Senhas em .env."

wait -n "$api_pid" "$web_pid"

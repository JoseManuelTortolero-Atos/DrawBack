#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="$root/docker-compose.oidc-it.yml"
env_file="$root/.env.calendar-morocco-oidc-it"
project="calendar-morocco-p02-oidc-it"
container="${project}-keycloak-1"

for tool in docker curl jq openssl; do
    command -v "$tool" >/dev/null 2>&1 || {
        printf 'Required tool is missing: %s\n' "$tool" >&2
        exit 2
    }
done
if ! docker image inspect quay.io/keycloak/keycloak:19.0.3 >/dev/null 2>&1; then
    printf 'Required isolated Keycloak image is not available locally; refusing an implicit image pull\n' >&2
    exit 2
fi
if docker ps -a --format '{{.Names}}' | grep -Fxq "$container"; then
    printf 'OIDC test container already exists; inspect it before reusing or removing it\n' >&2
    exit 3
fi
if [[ ! -f "$env_file" ]]; then
    umask 077
    printf 'CALENDAR_OIDC_IT_ADMIN_USER=oidc-it-admin\n' > "$env_file"
    printf 'CALENDAR_OIDC_IT_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 24)" >> "$env_file"
    printf 'CALENDAR_OIDC_IT_HUMAN_SECRET=%s\n' "$(openssl rand -hex 24)" >> "$env_file"
    printf 'CALENDAR_OIDC_IT_SERVICE_SECRET=%s\n' "$(openssl rand -hex 24)" >> "$env_file"
    printf 'CALENDAR_OIDC_IT_USER_PASSWORD=%s\n' "$(openssl rand -hex 24)" >> "$env_file"
fi
set -a
# This file is created locally with mode 0600 and ignored by Git.
. "$env_file"
set +a

compose=(docker compose --project-name "$project" --env-file "$env_file" -f "$compose_file")
"${compose[@]}" config --quiet
"${compose[@]}" up -d
id="$("${compose[@]}" ps --quiet keycloak)"
cleanup() {
    local current_id metadata
    current_id="$("${compose[@]}" ps --quiet keycloak)"
    if [[ -z "$current_id" ]]; then
        return
    fi
    metadata="$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' "$current_id")"
    if [[ "$current_id" != "$id" || "$metadata" != "$project|keycloak" ]]; then
        printf 'Refusing to clean up a container that does not match the isolated OIDC test identity\n' >&2
        return
    fi
    "${compose[@]}" down
}
trap cleanup EXIT
metadata="$(docker inspect --format '{{.State.Status}}|{{index .Config.Labels "com.docker.compose.project"}}|{{index .Config.Labels "com.docker.compose.service"}}' "$id")"
[[ "$metadata" == "running|$project|keycloak" ]] || {
    printf 'Isolated Keycloak container identity mismatch\n' >&2
    exit 3
}
[[ "$(docker port "$id" 8080/tcp)" == "127.0.0.1:18080" ]] || {
    printf 'Isolated Keycloak port binding mismatch\n' >&2
    exit 3
}

ready=false
for attempt in $(seq 1 90); do
    if curl --silent --fail http://127.0.0.1:18080/ >/dev/null; then
        ready=true
        break
    fi
    sleep 2
done
[[ "$ready" == true ]] || {
    printf 'Isolated Keycloak did not become ready on its loopback port\n' >&2
    exit 4
}

admin=(/opt/keycloak/bin/kcadm.sh)
kcadm() { "${compose[@]}" exec -T keycloak "${admin[@]}" "$@"; }
kcadm config credentials --server http://localhost:8080 --realm master \
    --user "$CALENDAR_OIDC_IT_ADMIN_USER" --password "$CALENDAR_OIDC_IT_ADMIN_PASSWORD"

create_realm() {
    local realm="$1"
    kcadm create realms -s "realm=$realm" -s enabled=true
    kcadm create roles -r "$realm" -s name=SCHEDULE_TEST
    kcadm create roles -r "$realm" -s name=SCHEDULE_SERVICE
    kcadm create clients -r "$realm" -s clientId=calendar-human-client -s enabled=true \
        -s protocol=openid-connect -s publicClient=false -s "secret=$CALENDAR_OIDC_IT_HUMAN_SECRET" \
        -s directAccessGrantsEnabled=true -s standardFlowEnabled=false -s serviceAccountsEnabled=false
    kcadm create users -r "$realm" -s username=calendar-user -s enabled=true
    kcadm set-password -r "$realm" --username calendar-user --new-password "$CALENDAR_OIDC_IT_USER_PASSWORD"
    kcadm add-roles -r "$realm" --uusername calendar-user --rolename SCHEDULE_TEST
}

create_realm calendar-it
create_realm wrong-issuer-it
kcadm create users -r calendar-it -s username=calendar-no-role -s enabled=true
kcadm set-password -r calendar-it --username calendar-no-role --new-password "$CALENDAR_OIDC_IT_USER_PASSWORD"
kcadm create clients -r calendar-it -s clientId=calendar-planning-service -s enabled=true \
    -s protocol=openid-connect -s publicClient=false -s "secret=$CALENDAR_OIDC_IT_SERVICE_SECRET" \
    -s standardFlowEnabled=false -s serviceAccountsEnabled=true
service_client_id="$(kcadm get clients -r calendar-it -q clientId=calendar-planning-service | jq -r '.[0].id')"
service_user_id="$(kcadm get "clients/$service_client_id/service-account-user" -r calendar-it | jq -r '.id')"
kcadm add-roles -r calendar-it --uid "$service_user_id" --rolename SCHEDULE_SERVICE

export CALENDAR_OIDC_IT_ISSUER_URI=http://127.0.0.1:18080/realms/calendar-it
export CALENDAR_OIDC_IT_WRONG_ISSUER_URI=http://127.0.0.1:18080/realms/wrong-issuer-it
export CALENDAR_OIDC_IT_HUMAN_CLIENT_ID=calendar-human-client
export CALENDAR_OIDC_IT_SERVICE_CLIENT_ID=calendar-planning-service

printf 'Verified isolated OIDC provider: %s (container %s, loopback port 18080)\n' "$CALENDAR_OIDC_IT_ISSUER_URI" "$container"
cd "$root"
mvn -f "$root/pom.xml" '-Dtest=OidcProviderIntegrationTest' test

#!/usr/bin/env bash
# Obtem um access token do Keycloak local via password grant.
# Uso: ./infra/local/get-token.sh
#      ./infra/local/get-token.sh comerciante2 comerciante2
#      KC_USERNAME=comerciante2 KC_PASSWORD=comerciante2 ./infra/local/get-token.sh
# O zsh reserva a variavel USERNAME para o login do sistema e nao repassa
# uma atribuicao no prefixo do comando. Por isso o script nao le USERNAME.
set -euo pipefail

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="${REALM:-fluxo-caixa}"
CLIENT_ID="${CLIENT_ID:-fluxo-caixa-app}"
KC_USER="${1:-${KC_USERNAME:-comerciante1}}"
KC_PASS="${2:-${KC_PASSWORD:-${PASSWORD:-comerciante1}}}"
SCOPE="${SCOPE:-openid lancamentos:write lancamentos:read consolidado:read}"

token=""
body=""
for _ in 1 2 3 4 5 6 7 8 9 10; do
  body="$(curl -sS -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "username=${KC_USER}" \
    --data-urlencode "password=${KC_PASS}" \
    --data-urlencode "scope=${SCOPE}" || true)"
  token="$(printf '%s' "${body}" | jq -r '.access_token // empty' 2>/dev/null || true)"
  if [[ -n "${token}" ]]; then
    printf '%s\n' "${token}"
    exit 0
  fi
  sleep 2
done

printf 'Falha ao obter token em %s. Resposta: %s\n' "${KEYCLOAK_URL}" "${body}" >&2
exit 1

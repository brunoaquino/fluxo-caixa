#!/usr/bin/env bash
# Lancamentos continua aceitando escrita com o consolidado parado.
# Depois que o consolidado volta, o saldo reflete os eventos que ficaram na fila.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LANCAMENTOS_URL="${LANCAMENTOS_URL:-http://localhost:8081}"
CONSOLIDADO_URL="${CONSOLIDADO_URL:-http://localhost:8082}"
DATA="${DATA_LANCAMENTO:-2099-01-15}"
COMPOSE=(docker compose -f "${ROOT}/infra/local/docker-compose.yml" --project-directory "${ROOT}/infra/local" --profile completo)

for cmd in curl jq uuidgen docker; do
  command -v "${cmd}" >/dev/null || { printf 'comando ausente: %s\n' "${cmd}" >&2; exit 1; }
done

TOKEN="$("${ROOT}/infra/local/get-token.sh")"

inicial_body="$(curl -sS -H "Authorization: Bearer ${TOKEN}" "${CONSOLIDADO_URL}/api/v1/consolidados/diario/${DATA}")"
inicial="$(printf '%s' "${inicial_body}" | jq -er '.saldo | tonumber')"
esperado="$(jq -nr --argjson inicial "${inicial}" '$inicial + 7')"

printf 'Parando consolidado-service. Saldo inicial em %s: %s\n' "${DATA}" "${inicial}"
"${COMPOSE[@]}" stop consolidado-service

post_esperando_201() {
  local payload="$1"
  local raw code
  raw="$(curl -sS -w $'\n%{http_code}' -X POST "${LANCAMENTOS_URL}/api/v1/lancamentos" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $(uuidgen | tr '[:upper:]' '[:lower:]')" \
    -d "${payload}")"
  code="$(printf '%s\n' "${raw}" | tail -n 1)"
  if [[ "${code}" != "201" ]]; then
    printf 'Lancamento com consolidado parado retornou %s (esperado 201)\n%s\n' "${code}" "${raw}" >&2
    "${COMPOSE[@]}" start consolidado-service || true
    exit 1
  fi
}

post_esperando_201 "$(jq -nc --arg data "${DATA}" '{tipo:"CREDITO",valor:10.00,dataLancamento:$data,descricao:"credito durante queda",categoria:"RESILIENCIA"}')"
post_esperando_201 "$(jq -nc --arg data "${DATA}" '{tipo:"DEBITO",valor:3.00,dataLancamento:$data,descricao:"debito durante queda",categoria:"RESILIENCIA"}')"
printf 'Dois lancamentos aceitos com o consolidado parado.\n'

"${COMPOSE[@]}" start consolidado-service

for _ in $(seq 1 40); do
  if curl -fsS "${CONSOLIDADO_URL}/actuator/health/readiness" >/dev/null; then
    break
  fi
  sleep 2
done

for _ in $(seq 1 40); do
  body="$(curl -sS -H "Authorization: Bearer ${TOKEN}" "${CONSOLIDADO_URL}/api/v1/consolidados/diario/${DATA}" || true)"
  if printf '%s' "${body}" | jq -e --argjson esperado "${esperado}" '((.saldo | tonumber) == $esperado)' >/dev/null; then
    printf 'Saldo convergiu para %s em %s.\n' "${esperado}" "${DATA}"
    exit 0
  fi
  sleep 2
done

printf 'Saldo nao convergiu para %s. Ultima resposta: %s\n' "${esperado}" "${body:-vazia}" >&2
exit 1

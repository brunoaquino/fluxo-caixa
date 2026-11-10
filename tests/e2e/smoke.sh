#!/usr/bin/env bash
# Smoke ponta a ponta: credito, debito, estorno e saldo consolidado.
# Credito 150, debito 40, estorno do debito (vira credito 40). Saldo esperado: 150.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LANCAMENTOS_URL="${LANCAMENTOS_URL:-http://localhost:8081}"
CONSOLIDADO_URL="${CONSOLIDADO_URL:-http://localhost:8082}"
DATA="${DATA_LANCAMENTO:-$(date +%F)}"
ESPERADO="${SALDO_ESPERADO:-150}"

for cmd in curl jq uuidgen; do
  command -v "${cmd}" >/dev/null || { printf 'comando ausente: %s\n' "${cmd}" >&2; exit 1; }
done

TOKEN="$("${ROOT}/infra/local/get-token.sh")"

post_lancamento() {
  local path="$1"
  local payload="$2"
  local raw code body
  raw="$(curl -sS -w $'\n%{http_code}' -X POST "${LANCAMENTOS_URL}${path}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: $(uuidgen | tr '[:upper:]' '[:lower:]')" \
    -d "${payload}")"
  code="$(printf '%s\n' "${raw}" | tail -n 1)"
  body="$(printf '%s\n' "${raw}" | sed '$d')"
  if [[ "${code}" != "201" && "${code}" != "200" ]]; then
    printf 'POST %s retornou %s\n%s\n' "${path}" "${code}" "${body}" >&2
    exit 1
  fi
  printf '%s' "${body}"
}

credito="$(post_lancamento "/api/v1/lancamentos" "$(jq -nc --arg data "${DATA}" '{tipo:"CREDITO",valor:150.00,dataLancamento:$data,descricao:"Venda balcao",categoria:"VENDAS"}')")"
credito_id="$(printf '%s' "${credito}" | jq -er '.id')"

debito="$(post_lancamento "/api/v1/lancamentos" "$(jq -nc --arg data "${DATA}" '{tipo:"DEBITO",valor:40.00,dataLancamento:$data,descricao:"Compra insumos",categoria:"INSUMOS"}')")"
debito_id="$(printf '%s' "${debito}" | jq -er '.id')"

post_lancamento "/api/v1/lancamentos/${debito_id}/estornos" '{"motivo":"estorno smoke"}' >/dev/null

saldo=""
for _ in $(seq 1 40); do
  body="$(curl -sS -H "Authorization: Bearer ${TOKEN}" "${CONSOLIDADO_URL}/api/v1/consolidados/diario/${DATA}" || true)"
  saldo="$(printf '%s' "${body}" | jq -r '.saldo // empty' 2>/dev/null || true)"
  if [[ -n "${saldo}" ]] && printf '%s' "${body}" | jq -e --argjson esperado "${ESPERADO}" '((.saldo | tonumber) == $esperado)' >/dev/null; then
    printf 'Smoke ok. data=%s credito=%s debito=%s saldo=%s\n' "${DATA}" "${credito_id}" "${debito_id}" "${saldo}"
    exit 0
  fi
  sleep 2
done

printf 'Saldo nao convergiu para %s. Ultima resposta: %s\n' "${ESPERADO}" "${body:-vazia}" >&2
exit 1

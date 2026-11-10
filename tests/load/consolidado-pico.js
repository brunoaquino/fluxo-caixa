import http from 'k6/http';
import { check } from 'k6';

const p95 = __ENV.P95_MS || '100';
const duration = __ENV.DURATION || '3m';
const postRate = Number(__ENV.POST_RATE || 2);
const dataRef = __ENV.DATA_LANCAMENTO || '2026-11-10';
const lancamentosUrl = __ENV.LANCAMENTOS_URL || 'http://localhost:8081';
const consolidadoUrl = __ENV.CONSOLIDADO_URL || 'http://localhost:8082';
const tokenUrl = __ENV.TOKEN_URL || 'http://localhost:8080/realms/fluxo-caixa/protocol/openid-connect/token';

export const options = {
  scenarios: {
    pico_consolidado: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: 40,
      maxVUs: 200,
      exec: 'consultarConsolidado',
    },
    misto_lancamentos: {
      executor: 'constant-arrival-rate',
      rate: postRate,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: 5,
      maxVUs: 30,
      exec: 'registrarLancamento',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{endpoint:consolidado}': [`p(95)<${p95}`],
    'http_req_duration{endpoint:lancamentos}': ['p(95)<500'],
  },
};

export function setup() {
  const response = http.post(
    tokenUrl,
    {
      grant_type: 'password',
      client_id: __ENV.CLIENT_ID || 'fluxo-caixa-app',
      username: __ENV.USERNAME || 'comerciante1',
      password: __ENV.PASSWORD || 'comerciante1',
      scope: 'openid lancamentos:write lancamentos:read consolidado:read',
    },
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
  );

  const ok = check(response, { 'token emitido': (res) => res.status === 200 });
  if (!ok) {
    throw new Error(`token HTTP ${response.status}: ${response.body}`);
  }
  return { token: response.json('access_token') };
}

export function consultarConsolidado(data) {
  const response = http.get(`${consolidadoUrl}/api/v1/consolidados/diario/${dataRef}`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { endpoint: 'consolidado' },
  });
  check(response, { 'consolidado 200': (res) => res.status === 200 });
}

export function registrarLancamento(data) {
  const response = http.post(
    `${lancamentosUrl}/api/v1/lancamentos`,
    JSON.stringify({
      tipo: 'CREDITO',
      valor: 1.0,
      dataLancamento: dataRef,
      descricao: 'carga k6',
      categoria: 'CARGA',
    }),
    {
      headers: {
        Authorization: `Bearer ${data.token}`,
        'Content-Type': 'application/json',
      },
      tags: { endpoint: 'lancamentos' },
    },
  );
  check(response, {
    'lancamento aceito': (res) => res.status === 201 || res.status === 200,
  });
}

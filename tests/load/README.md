# Carga do consolidado

`consolidado-pico.js` segura 50 requisições por segundo no `GET /api/v1/consolidados/diario/{data}` e, em paralelo, um fluxo menor de `POST /api/v1/lancamentos`.

Thresholds:

- `http_req_failed < 0.05` — o RNF de pico. O k6 conta status 0 e `>= 400`.
- p95 do consolidado `< 100` ms — o SLO com cache. Contra um ALB distante, suba o teto: `P95_MS=300`.
- p95 do POST de lançamentos `< 500` ms.

O teste passa se a taxa de falha ficar abaixo de 5% e a latência dentro do teto. Uma falha de threshold no final é o teste reprovando o ambiente, não um erro do script. Olhe `http_req_failed` separado de `http_req_duration`: perda acima de 5% quebra o RNF mesmo com p95 bom. p95 alto com pouca perda aponta cache frio, pool de banco ou pouca task.

```bash
make load
# ou
k6 run tests/load/consolidado-pico.js
k6 run -e DURATION=5m -e P95_MS=300 -e POST_RATE=5 tests/load/consolidado-pico.js
```

Pelo container, no Mac o host é `host.docker.internal`:

```bash
docker run --rm -i \
  -e LANCAMENTOS_URL=http://host.docker.internal:8081 \
  -e CONSOLIDADO_URL=http://host.docker.internal:8082 \
  -e TOKEN_URL=http://host.docker.internal:8080/realms/fluxo-caixa/protocol/openid-connect/token \
  grafana/k6 run - < tests/load/consolidado-pico.js
```

No Linux, `--network host` e as URLs `localhost` bastam.

Variáveis: `LANCAMENTOS_URL`, `CONSOLIDADO_URL`, `TOKEN_URL`, `DATA_LANCAMENTO`, `DURATION`, `P95_MS`, `POST_RATE`, `USERNAME`, `PASSWORD`, `CLIENT_ID`.

Contra o ALB da AWS, o WAF limita 30000 requisições por IP a cada 5 minutos (cerca de 100 req/s). O pico de 50 req/s de uma única máquina passa. Um teste mais agressivo precisa de mais IPs ou de um limite maior em `waf_rate_limit`.

## E2E

`tests/e2e/smoke.sh` pega o token, lança um crédito de 150 e um débito de 40, estorna o débito e espera o saldo do dia fechar em 150.

`tests/e2e/resiliencia.sh` lê o saldo de `2099-01-15`, para o container do consolidado, grava crédito 10 e débito 3 (os dois precisam voltar 201) e, depois de religar, espera o saldo subir 7.

```bash
make smoke
make resiliencia
```

Os dois precisam dos serviços no ar (`make up`) e de `jq`, `curl` e `uuidgen`.

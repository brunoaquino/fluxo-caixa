# Ambiente local

Compose em `infra/local`. Dois perfis:

- `infra` sobe só as dependências, para rodar o serviço pela IDE ou pelo `./mvnw`.
- `completo` sobe dependências e os dois serviços, com build do Dockerfile em `services/<servico>`.

O Compose não tem como expressar "sobe tudo por padrão e um perfil menor por cima". Os alvos do `Makefile` na raiz são o caminho esperado: `make infra-up` e `make up`.

```bash
make infra-up
make token
make infra-down
```

`make up` falha enquanto `services/*/Dockerfile` não existir.

## O que sobe

| Serviço | Porta no host | Credencial |
|---|---|---|
| postgres 17 | 5432 | bancos `lancamentos` e `consolidado`, usuário = senha = nome do banco. Superusuário `postgres`/`postgres` |
| redis 7.4 | 6379 | sem senha |
| localstack | 4566 | tópico `fluxo-caixa-lancamentos-events`, fila `consolidado-lancamentos-queue`, DLQ com `maxReceiveCount=5`, subscription com raw delivery |
| keycloak 26.4.7 | 8080 | admin `admin`/`admin`. Realm `fluxo-caixa` |
| otel-lgtm | 3000, 4317, 4318 | Grafana `admin`/`admin` |

Usuários do realm:

| Usuário | Senha | `comerciante_id` |
|---|---|---|
| `comerciante1` | `comerciante1` | `11111111-1111-1111-1111-111111111111` |
| `comerciante2` | `comerciante2` | `22222222-2222-2222-2222-222222222222` |

Client público `fluxo-caixa-app`, direct access grants. Escopos default: `lancamentos:write`, `lancamentos:read`, `consolidado:read`. O claim `comerciante_id` vai no access token.

```bash
infra/local/get-token.sh
infra/local/get-token.sh comerciante2 comerciante2
```

No zsh, `USERNAME=... comando` não funciona: o zsh reserva `USERNAME` para o login do sistema. Use o argumento ou `KC_USERNAME` / `KC_PASSWORD`.

## Variáveis dentro do compose

Os serviços usam o hostname da rede do compose (`postgres`, `redis`, `localstack`, `keycloak`, `otel-lgtm`). O issuer continua `http://localhost:8080/realms/fluxo-caixa`, que é o `iss` do token obtido no host. O JWK vem de `JWT_JWK_SET_URI=http://keycloak:8080/realms/fluxo-caixa/protocol/openid-connect/certs`. O Keycloak está com `KC_HOSTNAME=http://localhost:8080` e `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`.

`AWS_ACCESS_KEY_ID` e `AWS_SECRET_ACCESS_KEY` valem `test`. O LocalStack exige algum valor; isso não existe na task da AWS, que usa a task role.

Na IDE, com `make infra-up`, troque os hostnames por `localhost` e a fila por `http://localhost:4566/000000000000/consolidado-lancamentos-queue`. O resto do contrato está na seção 10 do blueprint.

O script de init do Postgres roda só na primeira subida do volume. Para recriar bancos e realm: `docker compose -f infra/local/docker-compose.yml --profile infra down -v`.

## Trace e correlation id no Grafana

Os serviços exportam trace OTLP para o `otel-lgtm` (Tempo) com `OTEL_PROPAGATORS=tracecontext,baggage`. O header `X-Correlation-Id` é da aplicação: volta na resposta e entra no log JSON como `correlationId`, junto com `traceId` e `spanId`.

O Alloy lê o stdout dos containers pelo socket do Docker e entrega no Loki do `otel-lgtm` (`http://otel-lgtm:3100`). No Grafana (`http://localhost:3000`, `admin`/`admin`):

- Tempo: serviço `lancamentos` ou `consolidado`.
- Loki: `{container="fluxo-caixa-lancamentos"} | json | traceId="<id>"` ou `correlationId="<uuid>"`.

O `traceId` do log é o mesmo do Tempo. Sem o Alloy, o trace continua no Tempo e o log fica só no `docker compose logs`.

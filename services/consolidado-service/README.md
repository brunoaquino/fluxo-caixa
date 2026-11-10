# consolidado-service

Projeção de leitura do saldo diário de um comerciante. O serviço não é a fonte da verdade dos lançamentos: consome o evento `LancamentoRegistrado` (SNS → SQS, raw delivery) e serve consultas materializadas. Se este serviço cair, o `lancamentos-service` continua gravando; as mensagens ficam retidas na fila.

Porta local: **8082**.

## Responsabilidade

- Aplicar cada lançamento no saldo do dia (`comercianteId`, `dataLancamento`), de forma comutativa.
- Garantir idempotência por `eventId`.
- Responder o saldo do dia, com saldo acumulado, e o relatório de um período de até 366 dias.
- Segurar 50 req/s com cache Redis e pool de conexões, degradando para o PostgreSQL se o Redis sair.

## Arquitetura

Hexagonal, pacote base `br.com.fluxocaixa.consolidado`:

```
domain/                 agregado SaldoDiario, VO Dinheiro, relatório — sem Spring
application/            casos de uso e ports
adapters/in/web/        REST, ProblemDetail, comerciante do JWT
adapters/in/messaging/  consumidor SQS e camada anticorrupção do evento
adapters/out/persistence/  JDBC + Flyway
adapters/out/cache/     Redis
config/                 segurança, beans, cliente SQS
```

O domínio não depende de Spring nem de adaptadores. A aplicação não depende de adaptadores. ArchUnit cobre essas duas regras.

## Concorrência e idempotência

A aplicação do evento é uma soma. A ordem de chegada não muda o saldo. Por isso o incremento é um UPSERT atômico, e não optimistic locking com retry:

```sql
INSERT INTO saldos_diarios (...)
VALUES (...)
ON CONFLICT (comerciante_id, data) DO UPDATE SET
    total_creditos = saldos_diarios.total_creditos + EXCLUDED.total_creditos,
    total_debitos = saldos_diarios.total_debitos + EXCLUDED.total_debitos,
    quantidade_lancamentos = saldos_diarios.quantidade_lancamentos + EXCLUDED.quantidade_lancamentos,
    versao = saldos_diarios.versao + 1,
    atualizado_em = EXCLUDED.atualizado_em
```

A versão continua sendo gravada (1 no primeiro evento, +1 nos seguintes) para auditoria. Ela não decide o commit.

A idempotência entra na mesma transação, antes do UPSERT:

```sql
INSERT INTO eventos_processados (event_id, processado_em)
VALUES (?, ?)
ON CONFLICT (event_id) DO NOTHING
```

Se duas entregas do mesmo `eventId` disputam a chave, o PostgreSQL segura a segunda até o commit da primeira. A perdedora recebe 0 linhas, não mexe no saldo e a mensagem é apagada da fila. Eventos diferentes do mesmo dia somam sem lost update.

A mensagem SQS só é apagada depois do commit. Falha transitória não apaga: a fila reentrega e, após 5 recebimentos, o redrive da infra manda para `consolidado-lancamentos-dlq`. JSON inválido, `eventType` diferente de `LancamentoRegistrado` ou `eventVersion` diferente de 1 é poison: log de erro e a mensagem também segue para a DLQ, sem ser apagada.

O consumidor faz long polling de 20s, lotes de até 10 e processa o lote em virtual threads. No desligamento ele para primeiro (`SmartLifecycle` na fase máxima), termina o lote já recebido e só então o restante da aplicação cai.

## Cache

Cache-aside no Redis, TTL de 30s. A chave de dados inclui uma versão por comerciante (`consolidado:versao:{id}`):

`consolidado:dia:{comerciante}:{versao}:{data}`
`consolidado:relatorio:{comerciante}:{versao}:{inicio}:{fim}`

Depois do commit, a versão é incrementada. A leitura seguinte não reutiliza o valor antigo. Chaves órfãs expiram no TTL. Se o incremento falhar, a resposta pode ficar defasada só até o TTL. Consistência eventual, janela máxima de 30s nessa falha; no caminho feliz a próxima leitura já vê o banco.

Se o Redis não responder, o adaptador registra um aviso e a consulta segue no PostgreSQL. O health de readiness não inclui Redis nem SQS: leitura continua no ar com o banco. O pool Hikari tem 20 conexões, timeout de conexão de 2s. Redis tem timeout de comando e de conexão de 200ms.

Resilience4j 2.4.0 publica `resilience4j-spring-boot4`, mas esse artefato depende do autoconfigure do Boot 4.0.0 e de `resilience4j-spring6`. Não entrou no serviço para não acoplar o Boot 4.1 a esse starter. Os limites estão nos timeouts do Hikari, do Lettuce, do Tomcat e do cliente SQS (a chamada cabe o long polling de 20s).

## API

Base `/api/v1`. Erros em `application/problem+json` (RFC 9457). OpenAPI em `/v3/api-docs` e `/swagger-ui.html`.

O `comercianteId` sai do JWT, claim `comerciante_id` (`app.security.comerciante-claim`). Escopo `consolidado:read`, com prefixo `app.security.scope-prefix` (vazio no local, `fluxo-caixa/` na AWS). A autoridade fica `SCOPE_<prefixo>consolidado:read`.

Dia sem movimento responde 200. Créditos, débitos, saldo e quantidade vêm zerados. `saldoAcumulado` continua sendo a soma dos saldos de todos os dias `<= data`, inclusive os anteriores. `atualizadoEm` fica nulo quando o dia não tem linha.

O relatório do período não tem corpo especificado no blueprint. O contrato implementado é:

```json
{
  "inicio": "2026-11-10",
  "fim": "2026-11-12",
  "dias": [
    {
      "data": "2026-11-10",
      "totalCreditos": 250.00,
      "totalDebitos": 50.00,
      "saldo": 200.00,
      "saldoAcumulado": 200.00,
      "quantidadeLancamentos": 3,
      "atualizadoEm": "2026-11-10T18:00:05Z"
    }
  ]
}
```

Um dia do intervalo sem lançamento entra na lista com zeros e com o acumulado correto. O intervalo máximo é 366 dias, inclusive.

```bash
curl -s http://localhost:8082/api/v1/consolidados/diario/2026-11-10 \
  -H "Authorization: Bearer $TOKEN"

curl -s "http://localhost:8082/api/v1/consolidados/diario?inicio=2026-11-01&fim=2026-11-10" \
  -H "Authorization: Bearer $TOKEN"
```

Actuator liberado sem token: `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, `/actuator/prometheus`.

## Variáveis

| Variável | Uso |
|---|---|
| `SERVER_PORT` | Porta HTTP. Padrão 8082 |
| `DB_URL` | JDBC do PostgreSQL do consolidado |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciais do banco |
| `AWS_REGION` | Padrão `sa-east-1` |
| `AWS_ENDPOINT_URL` | LocalStack. Vazio na AWS. No profile `local`, o padrão é `http://localhost:4566` |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Com endpoint override, o padrão local é `test` / `test` |
| `CONSOLIDADO_QUEUE_URL` | Fila `consolidado-lancamentos-queue`. Obrigatória com o consumo ligado |
| `JWT_ISSUER_URI` | Emissor do JWT |
| `JWT_JWK_SET_URI` | Opcional. Quando preenchida, as chaves vêm dessa URL e o `iss` continua sendo validado à parte |
| `APP_SECURITY_SCOPE_PREFIX` | Prefixo do escopo. Vazio no local; `fluxo-caixa/` na AWS |
| `APP_SECURITY_COMERCIANTE_CLAIM` | Claim do comerciante. Padrão `comerciante_id` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_SSL` | Redis. Padrão `localhost` / `6379` / `false` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Base OTLP. O Boot acrescenta `/v1/traces` |
| `SPRING_PROFILES_ACTIVE` | `local` ou `aws` |
| `APP_MESSAGING_CONSUMO_HABILITADO` | Desliga o poller. Os testes de API usam `false` |

Profiles: `local` aponta o endpoint AWS para o LocalStack. `aws` não define endpoint e usa o prefixo de escopo do Cognito.

## Observabilidade

- Logs JSON no formato logstash. Com um span aberto, o MDC entra no JSON (`traceId`, `spanId`).
- O processamento da mensagem abre uma observation e, se o atributo `traceparent` vier na mensagem, o contexto é continuado.
- Métricas: `consolidado.eventos.processados`, `consolidado.eventos.duplicados`, `consolidado.eventos.falhas` e o timer `consolidado.eventos.lag` (`now - occurredAt`, também no duplicado).
- Traces OTLP. Métricas OTLP ficam desligadas; o scrape é o Prometheus.
- Virtual threads ligadas (`spring.threads.virtual.enabled`).

## Como rodar

Pré-requisitos locais: Java 25, Docker, e o compose da infra (Postgres, Redis, LocalStack, Keycloak) sobe por outro componente.

```bash
cd services/consolidado-service
export SPRING_PROFILES_ACTIVE=local
export DB_URL=jdbc:postgresql://localhost:5432/consolidado
export DB_USERNAME=consolidado
export DB_PASSWORD=consolidado
export AWS_REGION=sa-east-1
export AWS_ENDPOINT_URL=http://localhost:4566
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export CONSOLIDADO_QUEUE_URL=http://localhost:4566/000000000000/consolidado-lancamentos-queue
export JWT_ISSUER_URI=http://localhost:8080/realms/fluxo-caixa
export JWT_JWK_SET_URI=http://keycloak:8080/realms/fluxo-caixa/protocol/openid-connect/certs
export REDIS_HOST=localhost
export REDIS_PORT=6379
export REDIS_SSL=false
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
./mvnw spring-boot:run
```

Imagem:

```bash
docker build -t consolidado-service:1.0.0 services/consolidado-service
```

## Testes

```bash
./mvnw verify
```

Sobe PostgreSQL 17, Redis 7 e LocalStack (SQS) via Testcontainers. Cobre:

- mensagem na fila atualiza o saldo;
- a mesma mensagem duas vezes conta uma vez;
- crédito, débito e estorno (crédito oposto) fecham o saldo;
- `GET` devolve `saldoAcumulado`, inclusive no dia sem movimento;
- Redis indisponível não estoura a leitura (teste de unidade do adaptador).

JaCoCo exige 80% de linhas em `domain` e `application`.

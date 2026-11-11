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
adapters/in/web/        REST, ProblemDetail, comerciante do JWT, correlation id
adapters/in/messaging/  consumidor SQS e camada anticorrupção do evento
adapters/out/persistence/  JDBC + Flyway
adapters/out/cache/     Redis com circuito e timeout
observabilidade/        correlation id e span CONSUMER
config/                 segurança, datasource, SQS
```

O domínio não depende de Spring nem de adaptadores. A aplicação não depende de adaptadores. ArchUnit cobre essas duas regras.

## Compatibilidade

Matriz presa ao que o serviço compila e testa. O Resilience4j 2.4.0 publica `resilience4j-spring-boot4`, que depende de `spring-boot-autoconfigure` 4.0.0 e de `resilience4j-spring6`. Por isso o serviço fica no Spring Boot **4.0.9** (última 4.0.x), com Java 25. O starter de AOP do Boot 4.0 é `spring-boot-starter-aspectj`.

| Componente | Versão |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.9 |
| Spring Framework | 7.0.10 |
| Resilience4j | 2.4.0 (`resilience4j-spring-boot4`) |
| Jackson | 3.1.7 (`tools.jackson`) |
| Hibernate ORM (gerido pelo Boot; o serviço usa JDBC) | 7.2.25.Final |
| Driver JDBC PostgreSQL | 42.7.14 |
| AWS JDBC Wrapper | 4.6.0 |
| Lettuce | 6.8.2.RELEASE |
| Testcontainers | 2.0.5 |
| AWS SDK for Java v2 | 2.57.12 |
| Flyway | 11.14.1 |
| springdoc-openapi | 3.0.3 (linha 3.0, construída sobre Boot 4.0) |
| Micrometer / Micrometer Tracing | 1.16.8 / 1.6.8 |

**Gatilho para o Boot 4.1:** subir quando o Resilience4j publicar um artefato compilado e testado contra `spring-boot-autoconfigure` 4.1.x (starter novo ou release que deixe de fixar o autoconfigure 4.0.0). No mesmo movimento, trocar o springdoc para a linha 3.1+ (a 3.1.0 já nasce em cima do Boot 4.1). Até lá, permanecer na última 4.0.x.

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

Se duas entregas do mesmo `eventId` disputam a chave, o PostgreSQL segura a segunda até o commit da primeira. A perdedora recebe 0 linhas, não mexe no saldo e a mensagem é apagada da fila. Eventos diferentes do mesmo dia somam sem lost update. Uma nova tentativa do Resilience4j depois de uma falha transitória repete essa mesma transação: ou reaplica o que não commitou, ou encontra o `eventId` e segue como duplicata.

A mensagem SQS só é apagada depois do commit. Falha transitória, esgotamento do retry ou bulkhead cheio não apagam: a fila reentrega e, após 5 recebimentos, o redrive da infra manda para `consolidado-lancamentos-dlq`. JSON inválido, corpo acima de 8 KiB, `eventType` diferente de `LancamentoRegistrado` ou `eventVersion` diferente de 1 é poison: log de erro sem o corpo e a mensagem também segue para a DLQ.

O consumidor faz long polling de 20s, pede `MessageAttributeNames=All` e `MessageSystemAttributeNames=All`, lotes de até 10 e processa o lote em virtual threads. No desligamento ele para primeiro (`SmartLifecycle` na fase máxima), termina o lote já recebido e só então o restante da aplicação cai.

O cliente HTTP é o `url-connection-client`, o mesmo do lançamentos, sem `apache5-client` e sem `httpcore5` (CVE-2026-54399 e CVE-2026-54428 no 5.3.6 que o SDK 2.57.12 traz). O `HttpURLConnection` encerra o socket no timeout. Com long polling de 20 s, o socket timeout é 25 s, a tentativa da API é 32 s e a chamada é 35 s. Abaixo de 20 s o poll morre antes da fila responder.

## Resiliência

Resilience4j 2.4.0, métricas no Micrometer/Prometheus (`resilience4j_circuitbreaker_*`, `resilience4j_retry_*`, `resilience4j_bulkhead_*`, `resilience4j_ratelimiter_*`).

| Peça | Nome | Comportamento |
|---|---|---|
| CircuitBreaker + TimeLimiter | `redis` | Timeout de 300 ms. Falhas abrem o circuito. Leitura e invalidação caem no PostgreSQL. `allowHealthIndicatorToFail=false`: estado OPEN aparece em `/actuator/health` como `CIRCUIT_OPEN` e não tira o readiness do ar |
| Bulkhead (semaphore) | `sqs-consumo` | Até 8 processamentos simultâneos, espera de 2 s. Se não houver permissão, a mensagem fica na fila |
| Retry | `banco-consumo` | 3 tentativas, backoff exponencial com jitter, só para falha transitória de banco/conexão. Não retenta poison. Depois de esgotar, a mensagem não é apagada |
| RateLimiter | `consultas` | 50 req/s. **Desligado** por padrão (`APP_RESILIENCE_RATE_LIMITER_HABILITADO=false`). Ligado, responde 429 em `application/problem+json` |

O readiness inclui só `readinessState` e `db`. Redis e o circuito ficam de fora. Liveness é só `livenessState`.

## Cache

Cache-aside no Redis, TTL de 30s. A chave de dados inclui uma versão por comerciante (`consolidado:versao:{id}`):

`consolidado:dia:{comerciante}:{versao}:{data}`
`consolidado:relatorio:{comerciante}:{versao}:{inicio}:{fim}`

Depois do commit, a versão é incrementada. A leitura seguinte não reutiliza o valor antigo. Chaves órfãs expiram no TTL. Se o incremento falhar ou o circuito abrir, a resposta sai do banco. Consistência eventual, janela máxima de 30s quando só a invalidação falha; no caminho feliz a próxima leitura já vê o banco.

O pool Lettuce tem timeout de comando e de conexão de 300 ms. O pool Hikari de escrita tem 10 conexões (tarefa de 1 vCPU; 10 tasks ficam perto de 100 conexões no primário), timeout de conexão de 3 s, `max-lifetime` de 15 min (abaixo da rotação de 30 dias do Secrets Manager) e `statement_timeout` de 3 s em cada conexão nova.

## Banco: segredo e réplica

- Sem `DB_SECRET_ARN`: driver `org.postgresql.Driver` com `DB_USERNAME` e `DB_PASSWORD`. Se a URL vier com `jdbc:aws-wrapper:`, o prefixo é removido.
- Com `DB_SECRET_ARN`: a URL efetiva é um único `jdbc:aws-wrapper:postgresql://...`, driver `software.amazon.jdbc.Driver`. Plugins `failover,awsSecretsManager` na instância. No host com `.proxy-` (RDS Proxy), só `awsSecretsManager`. Usuário e senha saem do segredo. Se `AWS_ENDPOINT_URL` estiver preenchido (local), o wrapper usa esse endpoint do Secrets Manager. O Terraform envia `jdbc:postgresql://`.
- `DB_READER_URL` opcional. Quando presente, `@Transactional(readOnly = true)` (consultas da API) vai para a réplica via `LazyConnectionDataSourceProxy` + routing datasource. O Flyway e o consumidor usam o primário. Sem a variável, há um único `DataSource`.

## Rastreabilidade

- HTTP propaga W3C Trace Context (`traceparent` / `tracestate`) pelo Micrometer Tracing com bridge OpenTelemetry. `management.tracing.propagation.type=w3c`.
- Header `X-Correlation-Id`: UUID. Se ausente ou inválido, o serviço gera outro, devolve no response e coloca `correlationId` no MDC. Valor inválido não é ecoado.
- Logs JSON (formato logstash) incluem `traceId`, `spanId`, `correlationId` e, no consumo, `eventId` e `comercianteId`.
- O consumidor extrai `traceparent`, `tracestate` e `correlationId` dos message attributes (raw delivery) e abre um span `CONSUMER` filho do produtor. Mensagem sem atributos de trace inicia um trace novo. O MDC é limpo no fim de cada mensagem, na mesma virtual thread.

## API

Base `/api/v1`. Erros em `application/problem+json` (RFC 9457). OpenAPI em `/v3/api-docs` e `/swagger-ui.html`. No perfil `local` o Swagger é público. No perfil `aws` o springdoc fica desligado e essas rotas não são públicas — o mesmo contrato do lançamentos. A borda não encaminha `/swagger-ui` nem `/actuator`.

O `comercianteId` sai do JWT, claim `comerciante_id` (`app.security.comerciante-claim`). Escopo `consolidado:read`, com prefixo `app.security.scope-prefix` (vazio no local, `fluxo-caixa/` na AWS). A autoridade fica `SCOPE_<prefixo>consolidado:read`.

CORS fica desligado enquanto `APP_SECURITY_CORS_ALLOWED_ORIGINS` não lista origens. Com a lista, só `GET`/`OPTIONS`, headers `Authorization`, `Content-Type`, `X-Correlation-Id`, `traceparent` e `tracestate`.

Headers de segurança na resposta: `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, CSP `default-src 'none'`, `Permissions-Policy` e HSTS.

Dia sem movimento responde 200. Créditos, débitos, saldo e quantidade vêm zerados. `saldoAcumulado` continua sendo a soma dos saldos de todos os dias `<= data`, inclusive os anteriores. `atualizadoEm` fica nulo quando o dia não tem linha.

O relatório do período:

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

Um dia do intervalo sem lançamento entra na lista com zeros e com o acumulado correto. O intervalo máximo é 366 dias, inclusive. Datas fora de `YYYY-MM-DD` respondem 400 sem repetir o valor recebido.

```bash
curl -s http://localhost:8082/api/v1/consolidados/diario/2026-11-10 \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: 11111111-1111-4111-8111-111111111111"

curl -s "http://localhost:8082/api/v1/consolidados/diario?inicio=2026-11-01&fim=2026-11-10" \
  -H "Authorization: Bearer $TOKEN"
```

Actuator liberado sem token: `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, `/actuator/prometheus`. O ALB deve usar readiness.

## Variáveis

| Variável | Uso |
|---|---|
| `SERVER_PORT` | Porta HTTP. Padrão 8082 |
| `DB_URL` | `jdbc:postgresql://` no contrato com o Terraform. Com segredo, o serviço aplica um único prefixo `jdbc:aws-wrapper:`. Os dois formatos são aceitos |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciais do banco quando `DB_SECRET_ARN` está ausente |
| `DB_SECRET_ARN` | ARN do segredo do RDS. Liga o wrapper. Failover só fora do endpoint do proxy |
| `DB_READER_URL` | JDBC opcional da réplica. Roteia transação somente leitura |
| `AWS_REGION` | Padrão `us-east-1`. Também região do Secrets Manager |
| `AWS_ENDPOINT_URL` | LocalStack. Vazio na AWS. No profile `local`, o padrão é `http://localhost:4566` |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Com endpoint override, o padrão local é `test` / `test` |
| `CONSOLIDADO_QUEUE_URL` | Fila `consolidado-lancamentos-queue`. Obrigatória com o consumo ligado |
| `JWT_ISSUER_URI` | Emissor do JWT |
| `JWT_JWK_SET_URI` | Opcional. Quando preenchida, as chaves vêm dessa URL e o `iss` continua sendo validado à parte |
| `APP_SECURITY_SCOPE_PREFIX` | Prefixo do escopo. Vazio no local; `fluxo-caixa/` na AWS |
| `APP_SECURITY_COMERCIANTE_CLAIM` | Claim do comerciante. Padrão `comerciante_id` |
| `APP_SECURITY_CORS_ALLOWED_ORIGINS` | Origens CORS separadas por vírgula. Vazio desliga CORS |
| `APP_RESILIENCE_RATE_LIMITER_HABILITADO` | `true` liga o limitador das consultas. Padrão `false` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_SSL` | Redis. Padrão `localhost` / `6379` / `false` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Base OTLP. O Boot acrescenta `/v1/traces` |
| `SPRING_PROFILES_ACTIVE` | `local` ou `aws` |
| `APP_MESSAGING_CONSUMO_HABILITADO` | Desliga o poller. Os testes de API usam `false` |

Profiles: `local` aponta o endpoint AWS para o LocalStack e abre o Swagger. `aws` não define endpoint, usa o prefixo de escopo do Cognito e autentica o Swagger.

## Observabilidade

- Logs JSON no formato logstash, com MDC (`traceId`, `spanId`, `correlationId`, `eventId`, `comercianteId`).
- Métricas de domínio: `consolidado.eventos.processados`, `consolidado.eventos.duplicados`, `consolidado.eventos.falhas` e o timer `consolidado.eventos.lag`.
- Traces OTLP. Métricas OTLP ficam desligadas; o scrape é o Prometheus.
- Virtual threads ligadas (`spring.threads.virtual.enabled`).

Planos de execução das consultas críticas: [docs/performance.md](docs/performance.md).

## Como rodar

Pré-requisitos locais: Java 25, Docker, e o compose da infra (Postgres, Redis, LocalStack, Keycloak) sobe por outro componente.

```bash
cd services/consolidado-service
export SPRING_PROFILES_ACTIVE=local
export DB_URL=jdbc:postgresql://localhost:5432/consolidado
export DB_USERNAME=consolidado
export DB_PASSWORD=consolidado
export AWS_REGION=us-east-1
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

Imagem (usuário não-root `app`, com `apt-get upgrade` na build):

```bash
docker build -t consolidado-service:1.0.0 services/consolidado-service
```

## Testes

```bash
./mvnw verify
```

Sobe PostgreSQL 17, Redis 7 e LocalStack (SQS e, no teste do segredo, Secrets Manager) via Testcontainers. Cobre saldo, idempotência, estorno, isolamento entre comerciantes, traceparent conhecido no log JSON, circuito aberto sem derrubar readiness, seleção do driver e Flyway com o wrapper, e roteamento leitura/escrita.

JaCoCo exige 80% de linhas em `domain` e `application`.

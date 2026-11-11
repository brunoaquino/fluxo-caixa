# lancamentos-service

Serviço de escrita do fluxo de caixa. Registra créditos e débitos, estorna lançamentos e publica o evento `LancamentoRegistrado` sem depender do serviço de consolidado.

## Responsabilidade

- Fonte da verdade dos lançamentos de um comerciante.
- Não altera nem apaga lançamento: correção é um novo lançamento de estorno (tipo oposto, mesmo valor e mesma data de competência).
- Grava o lançamento e o evento na mesma transação (transactional outbox) e um relay publica no tópico SNS `fluxo-caixa-lancamentos-events`.
- Se o consolidado estiver fora, este serviço continua aceitando lançamentos. As mensagens ficam no outbox e depois no SQS.

## Arquitetura interna

Hexagonal, pacote base `br.com.fluxocaixa.lancamentos`:

```
domain/                 agregado, value objects e evento — sem Spring
application/            casos de uso e ports
adapters/in/web/        API REST, Problem Details e claim do JWT
adapters/out/persistence/  JPA + Flyway
adapters/out/messaging/    publicador SNS e relay da outbox
config/                 segurança, AWS, OpenAPI e observabilidade
```

O `comercianteId` sai da claim JWT `comerciante_id` (configurável). Ele não vai no corpo nem na URL.

## Endpoints

Porta local `8081`. Base `/api/v1`. Erros em `application/problem+json` (RFC 9457).

| Método | Caminho | Escopo |
|---|---|---|
| POST | `/api/v1/lancamentos` | `lancamentos:write` |
| GET | `/api/v1/lancamentos/{id}` | `lancamentos:read` |
| GET | `/api/v1/lancamentos?data=YYYY-MM-DD&page=0&size=50` | `lancamentos:read` |
| POST | `/api/v1/lancamentos/{id}/estornos` | `lancamentos:write` |

OpenAPI: `/v3/api-docs` e `/swagger-ui.html`. No perfil `local` o Swagger é público. No perfil `aws` o springdoc fica desligado e essas rotas exigem autenticação se alguém as religar. Igual ao consolidado.

Probes: `/actuator/health/liveness` e `/actuator/health/readiness`. Métricas: `/actuator/prometheus`.

O header `Idempotency-Key` (UUID) é opcional no registro e no estorno. A primeira chamada responde `201`; a repetição da mesma chave e do mesmo conteúdo responde `200` com o mesmo recurso. A mesma chave com outro conteúdo responde `409`.

O valor sai como string decimal com duas casas (`"150.00"`), no mesmo formato do evento.

```bash
curl -sS -D- -X POST http://localhost:8081/api/v1/lancamentos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"tipo":"CREDITO","valor":150.00,"dataLancamento":"2026-11-10","descricao":"Venda balcão","categoria":"VENDAS"}'

curl -sS "http://localhost:8081/api/v1/lancamentos?data=2026-11-10&page=0&size=50" \
  -H "Authorization: Bearer $TOKEN"

curl -sS -X POST http://localhost:8081/api/v1/lancamentos/$ID/estornos \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"motivo":"Cliente devolveu"}'
```

Lista paginada:

```json
{
  "conteudo": [],
  "pagina": 0,
  "tamanho": 50,
  "totalElementos": 0,
  "totalPaginas": 0
}
```

## Variáveis de ambiente

| Variável | Uso |
|---|---|
| `SERVER_PORT` | Porta HTTP. Padrão `8081`. |
| `DB_URL` | JDBC do PostgreSQL do serviço. No perfil `aws` com segredo, pode ser `jdbc:postgresql://...`; o serviço prefixa `jdbc:aws-wrapper:`. |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciais do banco. Obrigatórias sem `DB_SECRET_ARN`. |
| `DB_SECRET_ARN` | ARN do segredo do RDS. Só ativa o AWS JDBC Wrapper no perfil `aws`. |
| `DB_READER_URL` | JDBC opcional da réplica. Ausente = um único DataSource. |
| `OUTBOX_RETENCAO` | Quanto tempo um evento publicado permanece na outbox. Padrão `7d`. |
| `OUTBOX_ADIAMENTO_CIRCUITO` | Quanto o relay espera com o circuito aberto. Padrão `30s`. |
| `OUTBOX_LIMPEZA_CRON` | Cron UTC da limpeza. Padrão `0 15 4 * * *`. |
| `AWS_REGION` | Região. Padrão `us-east-1`. |
| `AWS_ENDPOINT_URL` | Endpoint do LocalStack. Vazio na AWS. |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Usadas só quando `AWS_ENDPOINT_URL` está definido. Padrão `test`/`test`. Na AWS a task role entra pela cadeia padrão do SDK. |
| `LANCAMENTOS_TOPIC_ARN` | ARN do tópico `fluxo-caixa-lancamentos-events`. |
| `JWT_ISSUER_URI` | Issuer esperado do access token. |
| `JWT_JWK_SET_URI` | Opcional. Busca as chaves nesse URI e continua validando o issuer. |
| `APP_SECURITY_SCOPE_PREFIX` | Prefixo do escopo. Vazio no local; `fluxo-caixa/` na AWS. |
| `APP_SECURITY_COMERCIANTE_CLAIM` | Claim do comerciante. Padrão `comerciante_id`. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP HTTP (traces e métricas). Ex.: `http://localhost:4318`. |
| `SPRING_PROFILES_ACTIVE` | `local` ou `aws`. |

Profiles:

- `local`: detalhe do health liberado para diagnóstico.
- `aws`: `server.forward-headers-strategy=framework` para o ALB.

Threads virtuais e shutdown gracioso ficam ligados em qualquer profile.

## Rastreabilidade

- Propagação W3C (`traceparent` / `tracestate`) e `X-Amzn-Trace-Id` na entrada HTTP. O Boot não lê `OTEL_PROPAGATORS`; o bean compõe os dois. A flag `Sampled` do pai manda.
- Header `X-Correlation-Id`: UUID. Se vier ausente ou inválido, o serviço gera outro, devolve no response e grava no MDC como `correlationId`.
- Log JSON de console com `traceId`, `spanId`, `correlationId` e, quando a operação tem, `comercianteId`, `lancamentoId` e `eventId`.
- O POST e o estorno gravam `trace_parent`, `trace_state` e `correlation_id` na `outbox_eventos`.
- O relay não abre span quando a outbox está vazia. Com evento, abre um span `PRODUCER` filho do contexto gravado e publica os message attributes `eventType`, `traceparent`, `tracestate` (se houver), `correlationId` e `X-Amzn-Trace-Id`. O JSON do evento não muda.

## Resiliência da publicação SNS

O POST não chama o SNS. O relay publica com Resilience4j, instância `publicacaoSns`:

- Circuit breaker (janela de 10, mínimo 5 chamadas, 50% de falha, 30 s aberto).
- Retry com backoff exponencial e jitter (3 tentativas, 50 ms a 400 ms).
- Time limiter de 1 s, com timeout do cliente AWS em 2 s. O rate limiter não é usado neste serviço.
- Com o circuito aberto o lote fica na outbox (`proxima_tentativa_em` adiantada por `OUTBOX_ADIAMENTO_CIRCUITO`, padrão 30 s). Nada é descartado.
- Métricas no Micrometer (`resilience4j.circuitbreaker.*`, `resilience4j.retry.*`, `resilience4j.timelimiter.*`).
- O estado do circuito aparece em `/actuator/health` (`CIRCUIT_OPEN` / `CIRCUIT_HALF_OPEN`). O readiness inclui só `readinessState` e `db`, então o circuito aberto não tira a task do ALB.

Eventos já publicados saem da outbox no cron `OUTBOX_LIMPEZA_CRON` (padrão 04:15 UTC) depois de `OUTBOX_RETENCAO` (padrão 7 dias).

## Cliente HTTP da AWS

Os dois serviços usam o `url-connection-client` do AWS SDK e excluem o `apache5-client`. O SDK 2.57.12 traz `httpcore5` 5.3.6, afetado por CVE-2026-54399 e CVE-2026-54428. Tirar o cliente Apache tira a biblioteca do classpath. Um override para `httpcore5` 5.4.4 corrigiria o CVE e deixaria a dependência, sujeita ao próximo BOM. A publicação SNS não faz long polling: socket timeout e tentativa ficam em 2 s. O consolidado, que faz long polling de 20 s, sobe o socket para 25 s. O mesmo cliente JDK serve aos dois; o timeout acompanha a chamada.

## Banco, segredo e réplica

Sem `DB_SECRET_ARN`, o pool usa o driver PostgreSQL e `DB_USERNAME` / `DB_PASSWORD`. O Flyway usa esse mesmo pool de escrita.

O contrato com o Terraform é `DB_URL=jdbc:postgresql://...`. O serviço também aceita `jdbc:aws-wrapper:postgresql://` e não duplica o prefixo. No perfil `aws`, se `DB_SECRET_ARN` estiver definido, a URL efetiva é `jdbc:aws-wrapper:postgresql://...`. Na instância os plugins são `failover,awsSecretsManager`. Se o host contiver `.proxy-` (RDS Proxy), fica só `awsSecretsManager`: o failover do wrapper iria na instância e furaria o proxy. Sem segredo, o prefixo do wrapper é removido e o driver PostgreSQL usa `DB_USERNAME` / `DB_PASSWORD`. A rebusca do segredo acontece quando a autenticação falha depois da rotação. Hikari `max-lifetime` é 15 min, abaixo da rotação mínima de 30 dias. Cada conexão nova faz `SET statement_timeout = '30s'`.

`DB_READER_URL` é opcional. Quando existe, `@Transactional(readOnly = true)` vai para a réplica (`LazyConnectionDataSourceProxy` + roteamento). Sem a variável, leitura e escrita usam o primário. O Flyway continua no primário nos dois modos.

A listagem usa o índice `(comerciante_id, data_lancamento, criado_em DESC)`. O relay usa o índice parcial `outbox_eventos (proxima_tentativa_em, criado_em) WHERE publicado_em IS NULL`. Planos em `docs/performance.md`. `page` vai de 0 a 1000 e `size` de 1 a 100. Corpo de `/api/` acima de 8 KB responde 413. Campo desconhecido no JSON responde 400.

## Como rodar localmente

Suba Postgres, LocalStack e Keycloak pelo compose em `infra/local` (outro módulo) e então:

```bash
export SERVER_PORT=8081
export DB_URL=jdbc:postgresql://localhost:5432/lancamentos
export DB_USERNAME=lancamentos
export DB_PASSWORD=lancamentos
export AWS_REGION=us-east-1
export AWS_ENDPOINT_URL=http://localhost:4566
export LANCAMENTOS_TOPIC_ARN=arn:aws:sns:us-east-1:000000000000:fluxo-caixa-lancamentos-events
export JWT_ISSUER_URI=http://localhost:8080/realms/fluxo-caixa
export JWT_JWK_SET_URI=http://localhost:8080/realms/fluxo-caixa/protocol/openid-connect/certs
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318
export SPRING_PROFILES_ACTIVE=local
./mvnw spring-boot:run
```

Dentro do compose, `JWT_JWK_SET_URI` aponta para `http://keycloak:8080/...` e `JWT_ISSUER_URI` permanece o issuer do token obtido no host.

Imagem:

```bash
docker build -t lancamentos-service:local .
```

## Testes

Java 25 e Docker (Testcontainers sobe PostgreSQL 17 e LocalStack).

```bash
./mvnw verify
```

O `verify` roda testes de domínio, casos de uso, API (MockMvc + JWT), ArchUnit, roteamento de leitura, Resilience4j (retry, timeout e abertura do circuito) e o fluxo POST → outbox → SNS → SQS, incluindo `traceparent` e `correlationId`. O JaCoCo exige pelo menos 80% de linhas em `domain` e `application`.

## Matriz de compatibilidade

Versões resolvidas pelo BOM do Spring Boot 4.0.9, salvo onde há versão explícita no `pom.xml`.

| Componente | Versão |
|---|---|
| Java | 25 |
| Spring Boot | 4.0.9 |
| Spring Framework | 7.0.10 |
| Resilience4j | 2.4.0 (`resilience4j-spring-boot4`, `resilience4j-spring6`) |
| AspectJ | 1.9.25.1 (`spring-boot-starter-aspectj`) |
| Jackson | 3.1.7 |
| Hibernate | 7.2.25.Final |
| Flyway | 11.14.1 |
| Testcontainers | 2.0.5 |
| AWS SDK for Java v2 | 2.57.12 |
| AWS Advanced JDBC Wrapper | 4.6.0 |
| springdoc-openapi | 3.0.3 |
| Micrometer / tracing | 1.16.8 / 1.6.8 |
| Driver PostgreSQL | 42.7.14 |

**Quando voltar ao Boot 4.1.** O Resilience4j 2.4.0 declara `spring-boot-autoconfigure` 4.0.0. Boot 4.1 muda pacotes (health, starters) e o starter 2.4.0 não sobe nele. O gatilho é uma release do Resilience4j cujo `resilience4j-spring-boot4` dependa de `spring-boot-autoconfigure` 4.1.x (ou um artefato novo equivalente). Aí sobe junto o springdoc da linha 3.1+/3.2, que acompanha o Boot 4.1, e o BOM passa a escolher Framework, Jackson, Hibernate e Testcontainers. Até lá este serviço permanece em 4.0.9.

## Colunas além do desenho mínimo

- `idempotencia.impressao_digital`: distingue repetição legítima de reuso da mesma chave com outro corpo.
- `outbox_eventos.proxima_tentativa_em`: agenda o backoff do relay entre tentativas.
- `outbox_eventos.trace_parent`, `trace_state`, `correlation_id`: contexto W3C e correlação capturados no POST.

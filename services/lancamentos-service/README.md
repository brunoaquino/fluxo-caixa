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

OpenAPI: `/v3/api-docs` e `/swagger-ui.html`.

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
| `DB_URL` | JDBC do PostgreSQL do serviço. |
| `DB_USERNAME` / `DB_PASSWORD` | Credenciais do banco. |
| `AWS_REGION` | Região. Padrão `sa-east-1`. |
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

Threads virtuais, shutdown gracioso e log JSON (ECS) ficam ligados em qualquer profile. O log inclui `traceId`, `spanId` e `correlationId` (header `X-Correlation-Id`).

## Como rodar localmente

Suba Postgres, LocalStack e Keycloak pelo compose em `infra/local` (outro módulo) e então:

```bash
export SERVER_PORT=8081
export DB_URL=jdbc:postgresql://localhost:5432/lancamentos
export DB_USERNAME=lancamentos
export DB_PASSWORD=lancamentos
export AWS_REGION=sa-east-1
export AWS_ENDPOINT_URL=http://localhost:4566
export LANCAMENTOS_TOPIC_ARN=arn:aws:sns:sa-east-1:000000000000:fluxo-caixa-lancamentos-events
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

O `verify` roda testes de domínio, casos de uso, API (MockMvc + JWT), ArchUnit e o fluxo POST → outbox → SNS → SQS. O JaCoCo exige pelo menos 80% de linhas em `domain` e `application`.

## Colunas além do desenho mínimo

- `idempotencia.impressao_digital`: distingue repetição legítima de reuso da mesma chave com outro corpo.
- `outbox_eventos.proxima_tentativa_em`: agenda o backoff do relay entre tentativas.

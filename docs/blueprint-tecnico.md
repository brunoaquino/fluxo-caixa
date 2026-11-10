# Blueprint Técnico de Referência — Fluxo de Caixa

> Fonte única de verdade para nomes, contratos e convenções compartilhadas entre serviços, infraestrutura e documentação.
> Qualquer divergência deve ser resolvida a favor deste documento.

## 1. Visão geral

Solução para um comerciante controlar o fluxo de caixa diário (lançamentos de débito e crédito) e consultar o saldo diário consolidado.

- **Estilo**: microsserviços por *bounded context*, CQRS entre escrita (Lançamentos) e leitura (Consolidado), integração assíncrona orientada a eventos.
- **Requisito crítico (RNF)**: o serviço de Lançamentos **não pode ficar indisponível** se o Consolidado cair. Em pico, o Consolidado recebe **50 req/s** com no máximo **5% de perda**.
- **Como o RNF é atendido**: Lançamentos não chama o Consolidado de forma síncrona. Ele grava o lançamento e o evento na mesma transação (Transactional Outbox) e um *relay* publica no SNS. O Consolidado consome via SQS (com DLQ), é idempotente e serve leituras a partir de uma projeção materializada + cache Redis. Se o Consolidado cair, as mensagens ficam retidas no SQS (retenção de 14 dias) e são processadas quando ele voltar.

## 2. Stack

| Item | Escolha |
|---|---|
| Linguagem | Java 25 |
| Framework | Spring Boot 4.x (última versão estável disponível no Maven Central) |
| Build | Maven com wrapper (`mvnw`), um projeto Maven independente por serviço |
| Banco | PostgreSQL 17 (Amazon RDS em produção), um banco por serviço, migrações Flyway |
| Mensageria | Amazon SNS (tópico) + Amazon SQS (fila + DLQ). Localmente: LocalStack. Cliente: AWS SDK for Java v2 (sem Spring Cloud AWS) |
| Cache | Redis 7 / Valkey (Amazon ElastiCache em produção), apenas no Consolidado |
| Segurança | OAuth2/OIDC, JWT validado pelos serviços (Spring Security Resource Server). AWS: Amazon Cognito. Local: Keycloak |
| Observabilidade | Micrometer + OpenTelemetry (OTLP), logs JSON estruturados, Actuator. AWS: ADOT/CloudWatch/X-Ray. Local: `grafana/otel-lgtm` |
| Execução AWS | ECS Fargate, ALB com AWS WAF, região padrão `sa-east-1` |
| IaC | Terraform (provider AWS 6.x) |
| CI | GitHub Actions |

## 3. Layout do repositório

```
fluxo-caixa/
├── README.md
├── Makefile
├── docs/                         # toda a documentação de entrega
│   ├── blueprint-tecnico.md      # este arquivo
│   ├── 04-adr/                   # Architecture Decision Records (MADR)
│   └── ...
├── services/
│   ├── lancamentos-service/      # projeto Maven independente
│   └── consolidado-service/      # projeto Maven independente
├── infra/
│   ├── terraform/                # módulos + ambientes (dev, prod)
│   └── local/                    # docker-compose, init LocalStack, realm Keycloak
├── tests/
│   ├── load/                     # scripts k6
│   └── e2e/                      # smoke tests ponta a ponta (scripts)
└── .github/workflows/
```

## 4. Bounded contexts

| Contexto | Tipo | Serviço | Responsabilidade |
|---|---|---|---|
| Lançamentos (Gestão de Caixa) | Core | `lancamentos-service` | Registrar créditos e débitos, estornos, consultar lançamentos. Fonte da verdade. |
| Consolidação Diária | Core/Supporting | `consolidado-service` | Manter a projeção do saldo diário por comerciante e servir relatórios. |
| Identidade e Acesso | Generic | Cognito / Keycloak | Autenticação, emissão de tokens, escopos. |

Relação: Lançamentos é *upstream* (Published Language: eventos JSON versionados); Consolidado é *downstream* conformista com uma camada anticorrupção no adaptador de mensageria.

## 5. Modelo de domínio

### Lançamentos
- **Agregado `Lancamento`** (imutável após criado):
  - `id` (UUID v7 ou v4), `comercianteId` (UUID), `tipo` (`CREDITO` | `DEBITO`), `valor` (Value Object `Dinheiro`: `BigDecimal` escala 2, `> 0`, moeda `BRL`), `dataLancamento` (`LocalDate`, data de competência), `descricao` (1..255), `categoria` (opcional, até 60), `lancamentoEstornadoId` (UUID, nulo se não for estorno), `criadoEm` (`Instant`).
- **Invariantes**: valor positivo com no máximo 2 casas; descrição obrigatória; um lançamento só pode ser estornado uma vez; estorno de um estorno é proibido; estorno gera um novo lançamento de tipo oposto com o mesmo valor e mesma `dataLancamento` do original (o saldo do dia original é corrigido).
- Não existe update/delete de lançamento — correção é feita via estorno (trilha de auditoria).

### Consolidado
- **Agregado `SaldoDiario`** chave (`comercianteId`, `data`): `totalCreditos`, `totalDebitos`, `saldo` (= créditos − débitos), `quantidadeLancamentos`, `versao` (optimistic locking), `atualizadoEm`.
- **Saldo acumulado**: calculado na consulta como soma dos `saldo` de todos os dias `<= data` do comerciante.
- Aplicação de evento é comutativa (soma), portanto a ordem de chegada não importa; a idempotência é garantida pela tabela `eventos_processados(event_id PK)` gravada na mesma transação da atualização.

## 6. Contrato do evento (Published Language)

Publicado no tópico SNS, entregue no SQS com **raw message delivery = true** (o corpo da mensagem SQS é exatamente o JSON abaixo). Atributo de mensagem SNS/SQS `eventType` = `LancamentoRegistrado`.

```json
{
  "eventId": "0b8f6c1e-6d1a-4b8e-9a43-0f1b7e2f9c11",
  "eventType": "LancamentoRegistrado",
  "eventVersion": 1,
  "occurredAt": "2026-11-10T18:00:00.123Z",
  "lancamentoId": "5f0e1c7a-1d2b-4c3d-8e9f-0a1b2c3d4e5f",
  "comercianteId": "11111111-1111-1111-1111-111111111111",
  "tipo": "CREDITO",
  "valor": "150.00",
  "moeda": "BRL",
  "dataLancamento": "2026-11-10",
  "lancamentoEstornadoId": null
}
```

- `valor` é **string decimal** com 2 casas (evita perda de precisão).
- Um estorno é publicado como `LancamentoRegistrado` com tipo oposto e `lancamentoEstornadoId` preenchido — o Consolidado não precisa de lógica especial.
- Entrega *at-least-once*; consumidor deve ser idempotente por `eventId`.
- Evoluções: campos novos são aditivos; quebra de contrato exige `eventVersion` 2 e período de convivência.

## 7. APIs REST

Base path `/api/v1`. Erros em **RFC 9457 Problem Details** (`application/problem+json`). OpenAPI via springdoc (`/v3/api-docs`, `/swagger-ui.html`). O `comercianteId` **nunca** vem do corpo/URL: é extraído do JWT (claim configurável `app.security.comerciante-claim`, padrão `comerciante_id`).

### lancamentos-service (porta 8081)
| Método | Caminho | Escopo | Descrição |
|---|---|---|---|
| POST | `/api/v1/lancamentos` | `lancamentos:write` | Registra lançamento. Header opcional `Idempotency-Key` (UUID): a mesma chave e o mesmo corpo devolvem o mesmo recurso (201 na primeira, 200 na repetição); a mesma chave com corpo diferente responde **409**. |
| GET | `/api/v1/lancamentos/{id}` | `lancamentos:read` | Detalhe |
| GET | `/api/v1/lancamentos?data=YYYY-MM-DD&page=0&size=50` | `lancamentos:read` | Lista paginada por data |
| POST | `/api/v1/lancamentos/{id}/estornos` | `lancamentos:write` | Estorna lançamento (body opcional `{ "motivo": "..." }`). O header `Idempotency-Key` vale também aqui: a impressão digital é o id estornado mais o motivo. |

Request POST: `{ "tipo": "CREDITO", "valor": 150.00, "dataLancamento": "2026-11-10", "descricao": "Venda balcão", "categoria": "VENDAS" }`

- `valor` na requisição é número JSON (desserializado para `BigDecimal`); nas respostas e no evento é string decimal com 2 casas.
- Idempotência: primeira criação retorna **201 Created**; repetição com a mesma `Idempotency-Key` e a mesma impressão digital retorna **200 OK** com o mesmo recurso. A coluna `idempotencia.impressao_digital` guarda o SHA-256 do corpo canônico. Corpo diferente com a mesma chave responde **409**.
- Lista paginada: `{ "conteudo": [...], "pagina": 0, "tamanho": 50, "totalElementos": 1, "totalPaginas": 1 }`.
- Estorno: a `descricao` do lançamento de estorno é o `motivo` informado ou, se ausente, `Estorno do lançamento {id}`.

### consolidado-service (porta 8082)
| Método | Caminho | Escopo | Descrição |
|---|---|---|---|
| GET | `/api/v1/consolidados/diario/{data}` | `consolidado:read` | Saldo do dia: `{ data, totalCreditos, totalDebitos, saldo, saldoAcumulado, quantidadeLancamentos, atualizadoEm }`. Dia sem movimento retorna 200 com zeros. |
| GET | `/api/v1/consolidados/diario?inicio=YYYY-MM-DD&fim=YYYY-MM-DD` | `consolidado:read` | Relatório do período (máx. 366 dias): `{ inicio, fim, dias: [ <mesmo formato do saldo do dia> ] }` |

Escopos: autoridades `SCOPE_<prefixo><escopo>`. Propriedade `app.security.scope-prefix` (padrão vazio; na AWS/Cognito = `fluxo-caixa/`).

## 8. Arquitetura interna dos serviços (Hexagonal / Ports & Adapters)

Pacotes base: `br.com.fluxocaixa.lancamentos` e `br.com.fluxocaixa.consolidado`.

```
domain/          # entidades, value objects, eventos de domínio, exceções — sem Spring
application/     # casos de uso (ports in), ports out (interfaces)
adapters/
  in/web/        # controllers REST, DTOs, exception handler
  in/messaging/  # (consolidado) listener SQS
  out/persistence/  # JPA/JDBC
  out/messaging/    # (lancamentos) publicador SNS + outbox relay
  out/cache/        # (consolidado) Redis
config/
```

Regras verificadas por ArchUnit: `domain` não depende de Spring nem de `adapters`; `application` não depende de `adapters`.

## 9. Recursos de mensageria

No LocalStack os nomes são fixos. Na AWS o Terraform prefixa o ambiente (`fluxo-caixa-dev-` / `fluxo-caixa-prod-`). Os serviços não leem esses nomes: publicam em `LANCAMENTOS_TOPIC_ARN` e consomem `CONSOLIDADO_QUEUE_URL`.

| Recurso | Nome no local |
|---|---|
| Tópico SNS | `fluxo-caixa-lancamentos-events` |
| Fila SQS | `consolidado-lancamentos-queue` (visibility 60s, retenção 14d, long polling 20s) |
| DLQ | `consolidado-lancamentos-dlq` (maxReceiveCount = 5) |

Outbox (lancamentos): tabela `outbox_eventos(id UUID PK, aggregate_id, event_type, payload JSONB, criado_em, publicado_em NULL, tentativas INT, proxima_tentativa_em)`. O relay, a cada 500 ms, lê um lote de 100 com `SELECT ... FOR UPDATE SKIP LOCKED` onde `publicado_em` é nulo e `proxima_tentativa_em` já passou. Falha de publish grava a próxima tentativa com backoff exponencial até 60 s.

## 10. Variáveis de ambiente (contrato serviço ↔ infra)

Comuns:
| Variável | Exemplo local |
|---|---|
| `SERVER_PORT` | 8081 / 8082 |
| `DB_URL` | `jdbc:postgresql://localhost:5432/lancamentos` ou `/consolidado` |
| `DB_USERNAME` / `DB_PASSWORD` | `lancamentos`/`lancamentos`, `consolidado`/`consolidado` |
| `AWS_REGION` | `sa-east-1` |
| `AWS_ENDPOINT_URL` | `http://localhost:4566` no local. Na AWS a variável não é definida. Ausência ou string em branco faz o SDK usar o endpoint regional padrão; não se passa string vazia para `endpointOverride` |
| `JWT_ISSUER_URI` | `http://localhost:8080/realms/fluxo-caixa` |
| `APP_SECURITY_SCOPE_PREFIX` | vazio local; `fluxo-caixa/` na AWS |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318` |
| `SPRING_PROFILES_ACTIVE` | `local` / `aws` |

Específicas:
| Serviço | Variável | Exemplo local |
|---|---|---|
| lancamentos | `LANCAMENTOS_TOPIC_ARN` | `arn:aws:sns:sa-east-1:000000000000:fluxo-caixa-lancamentos-events` |
| consolidado | `CONSOLIDADO_QUEUE_URL` | `http://localhost:4566/000000000000/consolidado-lancamentos-queue` |
| consolidado | `REDIS_HOST` / `REDIS_PORT` / `REDIS_SSL` | `localhost` / `6379` / `false` |

Health checks: `/actuator/health/liveness` e `/actuator/health/readiness` (ALB usa readiness). Métricas: `/actuator/prometheus`.

## 11. Ambiente local (docker-compose em `infra/local`)

| Container | Porta | Observação |
|---|---|---|
| postgres | 5432 | cria bancos/usuários `lancamentos` e `consolidado` |
| redis | 6379 | |
| localstack | 4566 | cria tópico, fila, DLQ e subscription com raw delivery |
| keycloak | 8080 | realm `fluxo-caixa`, client `fluxo-caixa-app` (public, direct access grants), client scopes `lancamentos:write`, `lancamentos:read`, `consolidado:read` como default; usuário `comerciante1`/`comerciante1` com atributo `comerciante_id=11111111-1111-1111-1111-111111111111` mapeado como claim no access token |
| otel-lgtm | 3000 (Grafana), 4317, 4318 | |
| lancamentos-service | 8081 | build a partir de `services/lancamentos-service/Dockerfile` |
| consolidado-service | 8082 | build a partir de `services/consolidado-service/Dockerfile` |

Dentro da rede do compose, `JWT_ISSUER_URI` deve permanecer `http://localhost:8080/realms/fluxo-caixa` para bater com o `iss` do token obtido pelo host; usar `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` apontando para `http://keycloak:8080/realms/fluxo-caixa/protocol/openid-connect/certs` (variável `JWT_JWK_SET_URI`) e validar o issuer separadamente.

## 12. AWS (alvo)

- VPC em 3 AZs (subnets públicas, privadas de aplicação e privadas de dados), NAT Gateway (1 em dev, 1 por AZ em prod), VPC endpoints (ECR api/dkr, S3, Logs, SQS, SNS, Secrets Manager, STS).
- ALB público (HTTPS, ACM) + AWS WAF (managed rules + rate-based). Roteamento por caminho: `/api/v1/lancamentos*` → lancamentos, `/api/v1/consolidados*` → consolidado.
- ECS Fargate: cluster único, um serviço por microsserviço, mínimo 2 tasks em AZs distintas, auto scaling (CPU e `ALBRequestCountPerTarget`), deployment circuit breaker com rollback, Container Insights.
- RDS PostgreSQL **separado por serviço** em prod (isolamento de falha), Multi-AZ, criptografado com KMS, senha gerenciada pelo Secrets Manager.
- ElastiCache Redis/Valkey com réplica e failover automático (consolidado).
- SNS + SQS + DLQ criptografados (KMS) com políticas restritas.
- Cognito User Pool + Resource Server `fluxo-caixa` (escopos acima) + Pre Token Generation Lambda (v2) para incluir `comerciante_id` no access token.
- Observabilidade: CloudWatch Logs, ADOT collector como sidecar (traces → X-Ray, métricas → CloudWatch/AMP), dashboards e alarmes (5xx, latência p99, profundidade da fila, idade da mensagem mais antiga, mensagens na DLQ) com notificação via SNS.
- ECR com scan on push e imagens imutáveis.

## 13. Metas de NFR (SLOs)

| Métrica | Meta |
|---|---|
| Disponibilidade Lançamentos | 99,9% mensal, independente do Consolidado |
| Disponibilidade Consolidado | 99,5% mensal |
| Latência POST lançamento | p95 < 200 ms, p99 < 500 ms |
| Latência GET consolidado | p95 < 100 ms (cache), p99 < 300 ms |
| Throughput Consolidado | ≥ 50 req/s sustentados, erro ≤ 5% (meta interna ≤ 1%) |
| Atraso de consolidação (eventual consistency) | p95 < 5 s entre registro e reflexo no saldo |
| RPO / RTO | RPO ≤ 5 min (PITR RDS), RTO ≤ 1 h |

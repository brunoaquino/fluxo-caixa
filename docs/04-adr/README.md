# Decisões de arquitetura

Formato MADR. Status de todas: **Aceito**. Data de todas: **2026-11-10**. O contrato (nomes, portas, JSON) permanece no [blueprint](../blueprint-tecnico.md). Estes textos registram a alternativa e o preço.

| ADR | Decisão |
|---|---|
| [0001](0001-registrar-decisoes-com-adr.md) | Registrar decisão neste formato, no repositório |
| [0002](0002-microsservicos-por-bounded-context.md) | Dois microsserviços, um por bounded context |
| [0003](0003-cqrs-e-integracao-assincrona.md) | CQRS e integração por evento, sem chamada síncrona entre os serviços |
| [0004](0004-transactional-outbox.md) | Outbox transacional com relay, em vez de dual-write ou CDC |
| [0005](0005-sns-sqs-mensageria.md) | SNS + SQS Standard + DLQ |
| [0006](0006-postgresql-database-per-service.md) | PostgreSQL 17, instância RDS por serviço em produção |
| [0007](0007-java-25-spring-boot-4.md) | Java 25 (LTS) e Spring Boot 4 |
| [0008](0008-arquitetura-hexagonal.md) | Portas e adaptadores, vigiados por ArchUnit |
| [0009](0009-ecs-fargate.md) | ECS Fargate (ARM), mínimo de duas tasks |
| [0010](0010-terraform-iac.md) | Terraform, provider AWS 6.x |
| [0011](0011-oauth2-oidc-cognito.md) | OIDC com Cognito e Keycloak; JWT validado no serviço |
| [0012](0012-cache-redis-elasticache.md) | Cache-aside com Redis/Valkey só no consolidado |
| [0013](0013-observabilidade-otel.md) | OpenTelemetry, ADOT, CloudWatch e X-Ray |
| [0014](0014-idempotencia.md) | `Idempotency-Key` no POST e consumidor idempotente por `eventId` |
| [0015](0015-lancamentos-imutaveis-estorno.md) | Lançamento imutável; correção só por estorno |
| [0016](0016-representacao-monetaria.md) | `BigDecimal` / `NUMERIC(19,2)`; string no evento |
| [0017](0017-api-rest-versionada-rfc9457.md) | `/api/v1` e problemas RFC 9457 |
| [0018](0018-borda-alb-waf.md) | ALB + WAF na borda |
| [0019](0019-regiao-sa-east-1-lgpd.md) | Região `sa-east-1` por residência do dado |
| [0020](0020-estrategia-de-deploy.md) | Rolling com circuit breaker; blue/green como evolução |

Leitura mínima para o RNF crítico: 0002, 0003, 0004, 0005 e 0014.

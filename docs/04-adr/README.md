# Decisões de arquitetura

Formato MADR. Data de todas: **2026-11-10**. O contrato (nomes, portas, JSON) permanece no [blueprint](../blueprint-tecnico.md). Estes textos registram a alternativa e o preço.

Decisão substituída não é apagada. O status aponta o ADR vigente.

| ADR | Status | Decisão |
|---|---|---|
| [0001](0001-registrar-decisoes-com-adr.md) | Aceito | Registrar decisão neste formato, no repositório |
| [0002](0002-microsservicos-por-bounded-context.md) | Aceito | Dois microsserviços, um por bounded context |
| [0003](0003-cqrs-e-integracao-assincrona.md) | Aceito | CQRS e integração por evento, sem chamada síncrona entre os serviços |
| [0004](0004-transactional-outbox.md) | Aceito | Outbox transacional com relay, em vez de dual-write ou CDC |
| [0005](0005-sns-sqs-mensageria.md) | Aceito | SNS + SQS Standard + DLQ |
| [0006](0006-postgresql-database-per-service.md) | Aceito | PostgreSQL 17, instância RDS por serviço em produção |
| [0007](0007-java-25-spring-boot-4.md) | Substituído pelo 0023 | Java 25 e Spring Boot 4, sem patch pinado |
| [0008](0008-arquitetura-hexagonal.md) | Aceito | Portas e adaptadores, vigiados por ArchUnit |
| [0009](0009-ecs-fargate.md) | Aceito, ver 0026 | ECS Fargate (ARM), mínimo de duas tasks |
| [0010](0010-terraform-iac.md) | Aceito, ver 0029 | Terraform, provider AWS 6.x |
| [0011](0011-oauth2-oidc-cognito.md) | Aceito | OIDC com Cognito e Keycloak; JWT validado no serviço |
| [0012](0012-cache-redis-elasticache.md) | Aceito | Cache-aside com Redis/Valkey só no consolidado |
| [0013](0013-observabilidade-otel.md) | Aceito, ver 0025 e 0027 | OpenTelemetry, ADOT, CloudWatch e X-Ray |
| [0014](0014-idempotencia.md) | Aceito | `Idempotency-Key` no POST e consumidor idempotente por `eventId` |
| [0015](0015-lancamentos-imutaveis-estorno.md) | Aceito | Lançamento imutável; correção só por estorno |
| [0016](0016-representacao-monetaria.md) | Aceito | `BigDecimal` / `NUMERIC(19,2)`; string no evento |
| [0017](0017-api-rest-versionada-rfc9457.md) | Aceito | `/api/v1` e problemas RFC 9457 |
| [0018](0018-borda-alb-waf.md) | Substituído pelo 0022 | ALB público + WAF regional |
| [0019](0019-regiao-sa-east-1-lgpd.md) | Substituído pelo 0021 | Região `sa-east-1` por residência do dado |
| [0020](0020-estrategia-de-deploy.md) | Aceito, ver 0028 | Rolling com circuit breaker; blue/green como evolução |
| [0021](0021-regiao-us-east-1.md) | Aceito | Região `us-east-1`, transferência com salvaguarda |
| [0022](0022-cloudfront-vpc-origin.md) | Aceito, ver 0031 | CloudFront, WAF, VPC Origin e ALB interno |
| [0023](0023-resilience4j-e-versoes.md) | Aceito | Boot 4.0.9 e Resilience4j 2.4.0 |
| [0024](0024-rotacao-de-segredos.md) | Aceito | Rotação de RDS, KMS, ACM, JWKS e OIDC |
| [0025](0025-rastreabilidade-contexto.md) | Aceito | W3C e correlation id através da outbox e da fila |
| [0026](0026-ecs-agora-eks-evolucao.md) | Aceito | ECS agora; EKS acima de 8–10 serviços |
| [0027](0027-observabilidade-oss-vs-saas.md) | Aceito | CloudWatch e X-Ray agora; SaaS ou self-hosted depois |
| [0028](0028-gitops-argocd-evolucao.md) | Aceito | GitOps com ArgoCD quando houver EKS |
| [0029](0029-tres-ambientes-state-remoto.md) | Aceito | `dev`, `hml`, `prod`, state em S3 |
| [0030](0030-replica-leitura-estrategia-de-dados.md) | Aceito | Réplica desligada até gatilho; projeção já é o read model |
| [0031](0031-api-gateway-rest-regional.md) | Aceito | REST API regional atrás do CloudFront, VPC Link v2 no ALB |

Leitura mínima para o RNF crítico: 0002, 0003, 0004, 0005 e 0014. Para a borda: 0018 (histórico), 0022 e 0031.

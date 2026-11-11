# C4 — Nível 2: containers

Dois processos, dois bancos, um tópico, uma fila e uma DLQ. Cache só no consolidado. Identidade fora dos dois serviços.

```mermaid
C4Container
title Containers do Fluxo de Caixa
Person(comerciante, "Comerciante", "Operador do caixa ou integracao do PDV")
System_Ext(idp, "Cognito ou Keycloak", "OAuth2 OIDC")
System_Boundary(fluxo, "Fluxo de Caixa") {
  Container(cf, "CloudFront", "WAF CLOUDFRONT", "Unica entrada publica. TLS no edge. Cache desligado em /api")
  Container(apigw, "API Gateway", "REST regional", "Authorizer Cognito, usage plan, validacao. WAF regional anti-bypass")
  Container(alb, "ALB interno", "HTTP", "Sem IP publico. VPC Link v2 e rewrite do stage")
  Container(lanc, "lancamentos-service", "Java 25 / Spring Boot 4.0", "Porta 8081. Registra creditos debitos e estornos. Fonte da verdade")
  ContainerDb(dbl, "Postgres lancamentos", "PostgreSQL 17", "Tabelas lancamentos, idempotencia e outbox com trace_parent")
  Container(relay, "Outbox relay", "Agendador no lancamentos-service", "A cada 500 ms publica lote de 100 no SNS")
  ContainerQueue(sns, "Topico SNS", "fluxo-caixa-lancamentos-events", "Published Language LancamentoRegistrado v1")
  ContainerQueue(sqs, "Fila SQS", "consolidado-lancamentos-queue", "Retencao 14 dias. Visibility 60 s. Long polling 20 s")
  ContainerQueue(dlq, "DLQ", "consolidado-lancamentos-dlq", "Apos 5 recebimentos")
  Container(cons, "consolidado-service", "Java 25 / Spring Boot 4.0", "Porta 8082. Projecao do saldo e API de leitura")
  ContainerDb(dbc, "Postgres consolidado", "PostgreSQL 17", "Tabelas saldos_diarios e eventos_processados")
  ContainerDb(redis, "Cache", "Redis 7 local / Valkey no ElastiCache", "Cache-aside do consolidado")
}
Rel(comerciante, cf, "HTTPS JSON /api/v1", "TLS 1.2+")
Rel(cf, apigw, "HTTPS execute-api", "AllViewerExceptHostHeader e X-Origin-Verify")
Rel(apigw, alb, "VPC Link v2", "HTTP privado, timeout 10 s")
Rel(alb, lanc, "POST e GET /api/v1/lancamentos", "HTTP")
Rel(alb, cons, "GET /api/v1/consolidados", "HTTP")
Rel(apigw, idp, "Authorizer Cognito", "escopo por rota")
Rel(lanc, idp, "Valida JWT de novo", "JWKS")
Rel(cons, idp, "Valida JWT de novo", "JWKS")
Rel(lanc, dbl, "Grava lancamento, outbox e trace_parent", "JDBC")
Rel(relay, dbl, "Reserva nao publicados com SKIP LOCKED", "JDBC")
Rel(relay, sns, "Publica com traceparent", "AWS SDK v2")
Rel(sns, sqs, "Entrega com raw message delivery", "assinatura SNS")
Rel(sqs, dlq, "Redireciona apos maxReceiveCount 5", "redrive")
Rel(cons, sqs, "Consome", "long polling")
Rel(cons, dbc, "Aplica projecao e marca eventId", "JDBC")
Rel(cons, redis, "Le e invalida por geracao", "protocolo Redis")
```

O relay está desenhado como container lógico. No deploy ele é um agendador dentro do processo do `lancamentos-service`, não um terceiro serviço. Separá-lo no diagrama deixa visível que a publicação não está no caminho do POST. Subir um processo exclusivo de relay só vale se o agendamento competir com a API por CPU — não é o caso em 20 escritas/s.

| Container | Escala | Estado |
|---|---|---|
| CloudFront + WAF | Distribuição global, PoP GRU | Sem estado de negócio. `/api/*` não é cacheado. |
| API Gateway REST | Regional, stage `v1` | Sem estado de negócio. Rejeita JWT inválido antes da VPC. |
| ALB interno | Um, nas subnets privadas | Sem IP público. Dois target groups. Aceita o VPC Link. |
| `lancamentos-service` | 2+ tasks ECS em prod | Stateless. Estado no RDS de lançamentos. |
| `consolidado-service` | 2+ tasks ECS em prod | Stateless. Estado no RDS de consolidado e no cache. |
| Postgres (cada um) | RDS Multi-AZ em produção | Fonte durável. |
| SNS, SQS, DLQ | Gerenciados | Buffer entre os contextos. |
| Redis / Valkey | Réplica com failover em produção | Descartável. Perda do cache só aumenta latência. |
| Cognito / Keycloak | Gerenciado / container local | Fora do domínio. |

Portas locais: lançamentos 8081, consolidado 8082, Postgres 5432, Redis 6379, LocalStack 4566, Keycloak 8080, Grafana 3000, OTLP 4317 e 4318. Na AWS o cliente fala só com o CloudFront em 443. O API Gateway é regional e não é a URL que se publica. O ALB é interno, sem IP público, e as portas dos serviços ficam na rede privada. O relay continua sendo um agendador dentro do `lancamentos-service`, desenhado à parte para deixar visível que a publicação não está no caminho do POST. Com `enable_api_gateway = false`, o CloudFront volta a falar com o ALB pela VPC Origin e o container do gateway sai do caminho.

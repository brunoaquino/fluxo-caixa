# C4 — Nível 2: containers

Dois processos, dois bancos, um tópico, uma fila e uma DLQ. Cache só no consolidado. Identidade fora dos dois serviços.

```mermaid
C4Container
title Containers do Fluxo de Caixa
Person(comerciante, "Comerciante", "Operador do caixa ou integracao do PDV")
System_Ext(idp, "Cognito ou Keycloak", "OAuth2 OIDC")
System_Boundary(fluxo, "Fluxo de Caixa") {
  Container(lanc, "lancamentos-service", "Java 25 / Spring Boot 4", "Porta 8081. Registra creditos debitos e estornos. Fonte da verdade")
  ContainerDb(dbl, "Postgres lancamentos", "PostgreSQL 17", "Tabelas lancamentos, idempotencia e outbox_eventos")
  Container(relay, "Outbox relay", "Agendador no lancamentos-service", "A cada 500 ms publica lote de 100 no SNS")
  ContainerQueue(sns, "Topico SNS", "fluxo-caixa-lancamentos-events", "Published Language LancamentoRegistrado v1")
  ContainerQueue(sqs, "Fila SQS", "consolidado-lancamentos-queue", "Retencao 14 dias. Visibility 60 s. Long polling 20 s")
  ContainerQueue(dlq, "DLQ", "consolidado-lancamentos-dlq", "Apos 5 recebimentos")
  Container(cons, "consolidado-service", "Java 25 / Spring Boot 4", "Porta 8082. Projecao do saldo e API de leitura")
  ContainerDb(dbc, "Postgres consolidado", "PostgreSQL 17", "Tabelas saldos_diarios e eventos_processados")
  ContainerDb(redis, "Cache", "Redis 7 local / Valkey no ElastiCache", "Cache-aside do consolidado")
}
Rel(comerciante, lanc, "POST e GET /api/v1/lancamentos", "HTTPS JSON")
Rel(comerciante, cons, "GET /api/v1/consolidados", "HTTPS JSON")
Rel(lanc, idp, "Valida JWT", "JWKS")
Rel(cons, idp, "Valida JWT", "JWKS")
Rel(lanc, dbl, "Grava lancamento e outbox na mesma transacao", "JDBC")
Rel(relay, dbl, "Reserva nao publicados com SKIP LOCKED", "JDBC")
Rel(relay, sns, "Publica", "AWS SDK v2")
Rel(sns, sqs, "Entrega com raw message delivery", "assinatura SNS")
Rel(sqs, dlq, "Redireciona apos maxReceiveCount 5", "redrive")
Rel(cons, sqs, "Consome", "long polling")
Rel(cons, dbc, "Aplica projecao e marca eventId", "JDBC")
Rel(cons, redis, "Le e invalida por geracao", "protocolo Redis")
```

O relay está desenhado como container lógico. No deploy ele é um agendador dentro do processo do `lancamentos-service`, não um terceiro serviço. Separá-lo no diagrama deixa visível que a publicação não está no caminho do POST. Subir um processo exclusivo de relay só vale se o agendamento competir com a API por CPU — não é o caso em 20 escritas/s.

| Container | Escala | Estado |
|---|---|---|
| `lancamentos-service` | 2+ tasks ECS | Stateless. Estado no RDS de lançamentos. |
| `consolidado-service` | 2+ tasks ECS | Stateless. Estado no RDS de consolidado e no cache. |
| Postgres (cada um) | RDS Multi-AZ em produção | Fonte durável. |
| SNS, SQS, DLQ | Gerenciados | Buffer entre os contextos. |
| Redis / Valkey | Réplica com failover em produção | Descartável. Perda do cache só aumenta latência. |
| Cognito / Keycloak | Gerenciado / container local | Fora do domínio. |

Portas locais: lançamentos 8081, consolidado 8082, Postgres 5432, Redis 6379, LocalStack 4566, Keycloak 8080, Grafana 3000, OTLP 4317 e 4318. Na AWS o cliente fala só com o ALB em 443; as portas dos serviços ficam na rede privada.

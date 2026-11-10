# C4 — Nível 3: componentes do consolidado-service

Pacote `br.com.fluxocaixa.consolidado`. O HTTP só lê. Quem escreve a projeção é o listener SQS.

```mermaid
C4Component
title Componentes do consolidado-service
Container_Boundary(svc, "consolidado-service") {
  Component(api, "ConsultaConsolidadoController", "adapters.in.web", "GET do dia e do periodo. Relatorio inicio/fim/dias")
  Component(sec, "ConfiguracaoSeguranca", "config", "Escopo consolidado:read e claim comerciante_id")
  Component(ucGet, "ConsultarSaldoDiarioService e ConsultarRelatorioService", "application.servico", "Cache-aside e saldo acumulado")
  Component(ucApply, "AplicarLancamentoService", "application.servico", "UPSERT atomico e idempotencia por eventId")
  Component(dom, "SaldoDiario", "domain.model", "Invariante saldo = creditos - debitos")
  Component(acl, "TradutorEventoLancamento", "adapters.in.messaging", "JSON v1 vira comando. Versao desconhecida falha")
  Component(sqs, "ConsumidorSqsLancamentos", "adapters.in.messaging", "Long polling em CONSOLIDADO_QUEUE_URL")
  Component(jpa, "RepositorioConsolidadoJdbc", "adapters.out.persistence", "saldos_diarios e eventos_processados")
  Component(cache, "CacheSaldoRedis", "adapters.out.cache", "Versao por comerciante. TTL 30s. REDIS_SSL")
}
ContainerDb(db, "Postgres consolidado", "PostgreSQL 17", "Projecao")
ContainerDb(redis, "Redis ou Valkey", "cache", "Descartavel")
ContainerQueue(fila, "SQS", "consolidado-lancamentos-queue", "Buffer")
Container_Ext(idp, "Cognito ou Keycloak", "OIDC", "JWKS")
Rel(api, sec, "Autoriza a leitura")
Rel(api, ucGet, "GET /api/v1/consolidados")
Rel(ucGet, cache, "Busca pela geracao corrente")
Rel(ucGet, jpa, "Miss: le saldo e SUM acumulado")
Rel(sqs, acl, "Entrega o corpo raw")
Rel(acl, ucApply, "Comando AplicarLancamento")
Rel(ucApply, dom, "Aplica credito ou debito")
Rel(ucApply, jpa, "Atualiza saldo e insere event_id")
Rel(ucApply, cache, "Incrementa a geracao do comerciante")
Rel(jpa, db, "JDBC")
Rel(cache, redis, "GET SET INCR")
Rel(sqs, fila, "ReceiveMessage e DeleteMessage")
Rel(sec, idp, "Valida JWT")
```

| Decisão local | Efeito |
|---|---|
| ACL no `TradutorEventoLancamento`, não no agregado | `SaldoDiario` não conhece JSON. |
| `eventos_processados` na mesma transação do UPSERT | `ON CONFLICT DO NOTHING` no evento e `ON CONFLICT DO UPDATE` somando em `saldos_diarios`. Reentrega não duplica saldo. Não há lock otimista. |
| Falha de parse ou versão ignorada | A mensagem volta após o visibility timeout e, na quinta vez, cai na DLQ. Não se aplica “no melhor esforço”. |
| Redis fora | O caso de uso lê o Postgres. Latência piora, disponibilidade da leitura permanece, dentro do que o RDS aguenta (ver capacidade). |
| Geração de cache | Um estorno em data antiga invalida acumulado e período daquele comerciante sem varrer chave por chave. |

Não há porta de saída para o `lancamentos-service`. O consolidado não pergunta “qual é o lançamento?”. Se o evento não basta, o contrato está incompleto e a correção é versão nova do evento, não uma chamada síncrona de volta.

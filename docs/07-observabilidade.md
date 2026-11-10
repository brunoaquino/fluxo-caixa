# 07 — Observabilidade

Instrumentação: OpenTelemetry na aplicação, destino local `grafana/otel-lgtm`, destino AWS ADOT → CloudWatch e X-Ray ([ADR 0013](04-adr/0013-observabilidade-otel.md)). Log JSON com `trace_id`, `span_id`, `service.name` e `comerciante_id` quando a requisição já foi autenticada. Sem descrição de lançamento, sem valor em log de INFO, sem token.

## 1. Correlação

O POST gera um trace. O relay, ao publicar, copia o contexto W3C (`traceparent`) para atributo da mensagem SNS/SQS. O consumidor do consolidado abre o span filho. Um trace no Grafana ou no X-Ray mostra: HTTP, commit, publish, receive, upsert do saldo.

Se o `traceparent` não atravessar a fila, o desenho quebra no meio e o lag vira “dois sistemas”. Por isso a propagação é critério de aceite, não melhoria.

`eventId` e `lancamentoId` aparecem como atributos de span, não como dimensão de métrica (cardinalidade).

## 2. Métricas

### Sinais RED (serviços HTTP)

| Métrica | Lançamentos | Consolidado |
|---|---|---|
| Rate | req/s por rota e status | req/s por rota e status |
| Errors | 5xx / total, e 4xx separado (4xx não é queima de budget) | idem |
| Duration | histograma do POST, do GET por id e da lista | histograma do GET diário e do período |

### USE (recursos)

| Recurso | Utilização | Saturação | Erro |
|---|---|---|---|
| Task ECS | CPU e memória | throttling de CPU, fila do ALB | task stopped, restart |
| RDS | CPU, IOPS de gp3 | conexões / `max_connections`, fila de disco | failover, erro de conexão |
| Redis | CPU, memória, hit ratio | evictions, conexões | timeout, failover |
| SQS | mensagens visíveis, idade da mais antiga | não é CPU; a saturação é a idade | mensagens na DLQ |

### Golden signals do caixa (negócio)

| Métrica | Significado |
|---|---|
| `lancamentos_registrados_total{tipo}` | Fatos aceitos. Parado num horário que deveria vender é incidente de negócio, não só de CPU. |
| `lancamentos_estornados_total` | Volume de correção. Pico súbito merece olho humano. |
| `consolidacao_eventos_aplicados_total` | Consumo efetivo. |
| `consolidacao_lag_seconds` | `aplicadoEm − occurredAt`. SLO p95 &lt; 5 s em regime. |
| `outbox_pendente` | Linhas com `publicado_em` nulo. |
| `outbox_idade_segundos` | Idade da mais antiga não publicada. |
| `sqs_idade_mensagem_segundos` | `ApproximateAgeOfOldestMessage` da fila principal. |
| `sqs_mensagens_visiveis` | Backlog. |
| `sqs_dlq_visiveis` | Qualquer valor &gt; 0 é alarme. |
| `cache_hit_ratio` | Confere a premissa de 80% usada na capacidade. |

Métrica técnica sem a de lag não responde “o saldo está atrasado?”. Métrica de negócio sem RED não responde “a API está de pé?”. As duas ficam no mesmo dashboard, em painéis separados, para não misturar 4xx de estorno inválido com queda de saldo.

## 3. Dashboards

Um dashboard por serviço e um de ponta a ponta.

**Lançamentos.** RED do POST, conexões Hikari em uso versus máximo, `outbox_pendente`, idade da outbox, taxa de 409 (idempotência e estorno).

**Consolidado.** RED do GET, hit ratio, conexões Hikari, lag de consolidação, mensagens visíveis, idade da mensagem, DLQ.

**Ponta a ponta.** Taxa de POST, taxa de evento aplicado, lag p95, e um painel único “consolidado fora / lançamentos ok” para o RNF crítico ficar visível sem cruzar gráfico na mão.

No local, os mesmos painéis podem ser importados no Grafana do `otel-lgtm` quando os JSON existirem na infra. Até lá, o Grafana em `http://localhost:3000` já recebe trace e métrica via OTLP; o explore de traces é a verificação do caminho.

## 4. Alarmes

Limiares iniciais, para serem ajustados depois do primeiro k6 e da primeira semana de produção. Todos notificam um tópico SNS operacional (e-mail ou chat). O alarme não corrige sozinho, exceto o circuit breaker de deploy, que é outro mecanismo.

| Alarme | Condição | Por que esse número |
|---|---|---|
| 5xx lançamentos | &gt; 1% por 5 min, com pelo menos 20 req | Acima do ruído de uma falha isolada, abaixo de queimar o budget em silêncio |
| 5xx consolidado | &gt; 1% por 5 min | Meta interna de perda. O teto de 5% do enunciado não é o alarme: quando chega a 5%, o requisito já furou |
| p99 POST | &gt; 500 ms por 10 min | Igual ao SLO |
| p99 GET consolidado | &gt; 300 ms por 10 min | Igual ao SLO |
| Readiness | target unhealthy | ECS já substitui; o alarme existe para o humano saber |
| Outbox pendente | &gt; 100 linhas ou idade &gt; 30 s por 5 min | Relay de 500 ms não justifica 30 s. SNS ou relay parado |
| Idade da mensagem SQS | &gt; 30 s por 5 min em regime | Acima do SLO de 5 s com folga para um retry. Durante catch-up anunciado, o alarme pode ser silenciado com prazo |
| DLQ &gt; 0 | qualquer mensagem | Poison message é sempre anormal |
| RDS conexões | &gt; 70% de `max_connections` | Antes de recusar conexão nova. No small, 70% de ~225 é ~157, muito acima das 60 do teto de tasks — se disparar, há leak |
| RDS CPU | credit balance caindo por 30 min | Instância T estourando o baseline. Gatilho para medium |
| Redis evictions | &gt; 0 sustentado | Memória curta para o working set |
| Falha de cache | taxa de erro do cliente Redis &gt; 5% por 5 min | Degradação para o Postgres acontecendo de verdade |

Alarme de “fila com mensagem” não existe. Fila com mensagem é o sistema funcionando. O sinal de problema é a idade.

## 5. Runbooks

### Saldo atrasado, POST normal

1. Ver `consolidacao_lag_seconds` e a idade da mensagem mais antiga.
2. Se a outbox está velha, o relay ou o SNS parou. Ver log do `lancamentos-service` e a métrica de erro do publish. A fila ainda não recebeu o evento.
3. Se a outbox está em dia e a idade da fila sobe, o consumidor parou ou está lento. Ver readiness do consolidado e erro de banco.
4. Se há DLQ, ir para o runbook de DLQ. Não reprocessar no escuro.
5. Comunicar: lançamento está durável; o relatório é que atrasou. Não pedir para o comerciante “lançar de novo”.

### Consolidado fora

1. Confirmar que o target group de lançamentos segue saudável. Se não segue, não é o incidente “isolado” e o RNF crítico está sendo violado — tratar como incidente de lançamentos.
2. Deixar a fila acumular. Não desligar o relay: a outbox esvazia para a fila, que é o buffer de 14 dias.
3. Corrigir a causa (deploy, banco, config).
4. Ao voltar, observar a taxa de drenagem. Premissa: ~100 evt/s por task. 72 mil mensagens (1 h a 20 evt/s) levam cerca de 6 minutos com duas tasks.
5. O SLO de lag de 5 s não vale durante o catch-up. O alarme de idade fica silenciado só nessa janela.

### Mensagem na DLQ

1. Ler o corpo e o atributo `eventType` / versão. Não logar descrição se um dia ela passar a vir no evento; hoje o v1 não tem.
2. Se for bug corrigido e o `eventId` ainda for válido, redrive para `consolidado-lancamentos-queue`. O consumidor idempotente torna o redrive seguro.
3. Se for versão desconhecida, o produtor publicou contrato que o consumidor não aceita. Parar o produtor novo, não alargar o consumidor “para não falhar”.
4. Se for lixo, descartar com registro do `eventId` e da razão. Não editar JSON na mão para forçar a soma.

### POST lento

1. Separar tempo de JWT, tempo de commit e tempo total no trace.
2. Se o commit domina, olhar lock, CPU credit do RDS de lançamentos e tamanho da outbox (índice da parcial `publicado_em IS NULL` inchado).
3. Se o JWT domina, o JWKS não está em cache ou o Cognito está inalcançável (NAT, DNS). Várias tasks falhando juntas apontam para egress, não para um comerciante.

### Redis fora

1. Confirmar que o GET ainda retorna 200 e que a latência subiu, não que o target sumiu.
2. Se o readiness caiu, a probe está errada (Redis não pode matar a task). Corrigir a probe antes de escalar task, senão o ECS reinicia tudo em círculo.
3. Failover do ElastiCache ou recriação do nó. A projeção no Postgres é a fonte.

### Erro de budget

1. Se a queima rápida disparou, parar deploy que não seja correção.
2. Olhar se a causa é o WAF (403 súbito em IP legítimo) antes de culpar a task. 403 não é 5xx, mas o cliente vive os dois como “sistema fora”.

## 6. Ambiente local

`grafana/otel-lgtm` sobe com o Compose em `infra/local`.

| Porta | Uso |
|---|---|
| 3000 | Grafana |
| 4317 | OTLP gRPC |
| 4318 | OTLP HTTP, valor de `OTEL_EXPORTER_OTLP_ENDPOINT` |

Os serviços usam o perfil `local` e exportam OTLP para esse endpoint. Credencial do Grafana, quando a imagem pedir, é a padrão da imagem (`admin` / `admin`). Não se aponta esse Grafana para dado de produção.

O que validar uma vez, com `make up` e um POST:

1. O trace do POST aparece.
2. Depois de alguns segundos, o span do consumidor aparece no mesmo trace ou com link pelo `eventId`.
3. A métrica de lançamento registrado incrementa.

Sem isso, o dashboard de produção nasce cego. O passo está no [README da raiz](../README.md).

## 7. O que fica de fora de propósito

Profiling contínuo, RUM de frontend (não há frontend), log de SQL em INFO, e métrica por comerciante. A última explodiria a conta do CloudWatch e ainda vazaria cardinalidade de tenant para um backend de métrica. Contagem por comerciante, se o negócio pedir, é consulta no banco, não dimensão de métrica.

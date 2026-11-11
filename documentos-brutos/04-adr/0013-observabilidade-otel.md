# Observabilidade com OpenTelemetry, ADOT, CloudWatch e X-Ray

- **Status:** Aceito. A propagação do contexto pela fila está no [ADR 0025](0025-rastreabilidade-contexto.md). A comparação com SaaS e com stack self-hosted está no [ADR 0027](0027-observabilidade-oss-vs-saas.md).
- **Data:** 2026-11-10

## Contexto e problema

Um 201 no lançamento e um saldo errado dois segundos depois não cabem no mesmo log se os dois serviços, a fila e o banco não compartilharem um identificador. O pico, a DLQ e a outbox são sintomas operacionais, não só técnicos: fila crescendo é caixa sem relatório.

## Drivers

- Os três pilares: log, métrica e trace, correlacionados.
- Funcionar igual no Docker (Grafana LGTM) e na AWS (CloudWatch e X-Ray).
- Métrica de negócio mínima: lançamento registrado, consolidação aplicada, lag.
- Sem agente proprietário que prenda a telemetria a um fornecedor só.

## Opções consideradas

### 1. Log em texto e métrica de CPU do console

- Positivo: zero esforço.
- Negativo: não acha o evento. Não alarma DLQ. Não distingue erro de lançamentos de erro de consolidado. Reprova o diferencial de observabilidade do desafio e, na prática, reprova a operação do RNF.

### 2. Agente de APM proprietário

- Positivo: dashboard pronto, profiling.
- Negativo: licença, agente fechado, outro formato. A estimativa de custo trata licença de software como zero; um APM comercial quebraria essa premissa sem necessidade neste porte.

### 3. OpenTelemetry (OTLP) na aplicação, ADOT na AWS, `grafana/otel-lgtm` no local

Micrometer + OTel. Log JSON estruturado com `trace_id` e `span_id`. Actuator: liveness, readiness, Prometheus. Na AWS, sidecar ADOT exporta trace para X-Ray e métrica para CloudWatch. No local, `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318` e Grafana em `localhost:3000`.

- Positivo: um SDK, dois destinos. O teste local mostra o trace do POST até o consumidor. Alarmes de 5xx, p99, profundidade da fila, idade da mensagem, DLQ e outbox pendente têm para onde apontar. Sem licença.
- Negativo: sidecar custa CPU dentro da task (o consolidado reserva 1 vCPU / 2 GiB e o lançamentos 0,5 vCPU / 1 GiB, sidecar incluso). Cardinalidade de label mal escolhida (por exemplo `lancamentoId` como dimensão de métrica) explode a conta do CloudWatch. X-Ray tem preço por trace; amostragem é obrigatória, não opcional. O dashboard de produção está no módulo Terraform de observabilidade. O endpoint de interface do X-Ray existe para o ADOT não precisar de egress aberto.

## Decisão

Opção 3. Amostragem de cabeça. Requisição pela borda: decide o API Gateway, com a regra padrão do X-Ray (a primeira requisição de cada segundo e 5% das demais), e os serviços seguem a flag `Sampled` do pai. Trace sem pai: 1.0 em dev, 0.1 em hml e prod, por `MANAGEMENT_TRACING_SAMPLING_PROBABILITY`. Erro não tem retenção garantida: isso pede tail sampling no ADOT, que fica como evolução. O correlation id e o log cobrem o erro não amostrado. Log em JSON, nível INFO, sem payload de lançamento completo no log (descrição pode ser dado pessoal; o id basta).

Métricas de domínio, não só RED: contador de lançamentos por tipo, contador de eventos aplicados, gauge de outbox não publicada, lag de consolidação (instante do evento versus instante da aplicação).

## Consequências

### Positivas

- O runbook de “saldo atrasado” começa pelo lag e pela idade da mensagem, não por uma opinião.
- Trocar o backend de observabilidade no futuro não troca a instrumentação.

### Negativas

- ADOT sidecar e a aplicação precisam concordar na propagação de contexto W3C até o atributo da mensagem SNS/SQS. Se o relay não copiar o `traceparent` para a mensagem, o trace quebra no meio e o desenho perde metade do valor. Isso é critério de aceite da instrumentação, não enfeite.
- Custo de log cresce com DEBUG esquecido em produção. O perfil `aws` não habilita DEBUG de framework.

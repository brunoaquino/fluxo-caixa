# Rastreabilidade e propagação de contexto

- **Status:** Aceito
- **Data:** 2026-11-10
- **Complementa:** [ADR 0013](0013-observabilidade-otel.md)

## Contexto e problema

O ADR 0013 escolheu OpenTelemetry, ADOT, CloudWatch e X-Ray. O trace do POST morria no commit: o relay roda noutra thread, o SNS não carrega o contexto W3C sozinho, e o consumidor abria um trace novo. O lag de consolidação virava “dois sistemas” no Grafana. Suporte com um id na mão não achava a linha do outro serviço.

## Drivers

- Um trace só, do POST ao `UPSERT` do saldo, também no X-Ray.
- Um identificador que o cliente possa guardar e devolver, sem ser o trace id (o cliente não tem obrigação de falar W3C).
- O contrato JSON do evento estável. Telemetria não entra no corpo.
- Log JSON em que o campo exista de verdade, com exemplo, não só o nome da chave num diagrama.

## Opções consideradas

### 1. Só `eventId` no log

- Positivo: zero propagação. O id de negócio já existe.
- Negativo: o `eventId` nasce no commit. O tempo de JWT, a ida ao banco e a recusa 409 não têm `eventId`. O cliente não recebe esse id antes do 201. Não dá para correlacionar o 401.

### 2. `traceparent` dentro do JSON do evento

- Positivo: um campo só, o consumidor que já parseia o corpo vê o contexto.
- Negativo: toda versão do contrato e todo consumidor de BI passam a carregar telemetria. O JSON deixa de ser só fato contábil. A raw delivery continua, mas o payload inchou com dado que não é linguagem publicada.

### 3. W3C no HTTP, coluna na outbox, atributo na mensagem

`traceparent` e `tracestate` entram e saem no HTTP pelo Micrometer Tracing com a ponte OpenTelemetry. `X-Correlation-Id` é aceito se for UUID; se vier ausente ou inválido, o serviço gera outro, devolve no response e guarda no MDC como `correlationId`.

No commit, a linha da outbox grava `trace_parent`, `trace_state` e `correlation_id`. O relay, noutra thread, abre um span `PRODUCER` filho desse pai e publica os atributos `traceparent`, `tracestate` (se houver), `correlationId` e `eventType`, todos `String`. O corpo não muda.

O consumidor pede os atributos (`MessageAttributeNames=All`), abre um span `CONSUMER` filho do produtor, preenche o MDC e limpa no fim. Log JSON leva `traceId`, `spanId`, `correlationId` e, quando existirem, `comercianteId`, `lancamentoId`, `eventId`.

- Positivo: o contrato da seção 6 do blueprint permanece. O trace atravessa a fronteira assíncrona. O cliente guarda um UUID, não um header de trace.
- Negativo: esquecer `MessageAttributeNames=All` quebra a propagação em silêncio (o corpo chega, o contexto não). Isso é teste de contrato, não convenção oral. O formato do trace no X-Ray não é o hex de 32 caracteres do log; o [documento 07](../07-observabilidade.md) diz como navegar.

## Decisão

Opção 3. Amostragem segue a do ADR 0013 (cabeça no ingress: regra do X-Ray no API Gateway; trace sem pai a 10% em hml e prod). O filho herda a decisão de amostragem do pai: um POST amostrado produz o consumidor amostrado, e um POST não amostrado não acende o consumidor. Amostrar os dois lados de forma independente faria o trace “sumir no meio” justamente nos pedidos que se queria ver.

`comercianteId` é atributo de span e campo de log. Não é label de métrica.

## Consequências

### Positivas

- Um id devolvido no 201 alcança o log do consolidado.
- Dá para distinguir falha de publish (span produtor sem filho consumidor) de falha de projeção (filho consumidor com erro).

### Negativas

- Três ids na mesa (`correlationId`, `traceId`, `eventId`). O [documento de integração](../03-arquitetura/padroes-de-integracao.md) fixa o papel de cada um. Usar um no lugar do outro é bug de suporte.
- A coluna nova na outbox é obrigatória no insert do POST. Relay que publica sem reler a coluna recria o trace quebrado que este ADR existe para acabar.

# Fluxos de dados

Nomes e tempos seguem o blueprint: tópico `fluxo-caixa-lancamentos-events`, fila `consolidado-lancamentos-queue`, DLQ `consolidado-lancamentos-dlq`, relay a cada 500 ms, visibility 60 s, `maxReceiveCount` 5, atraso de consolidação p95 &lt; 5 s.

## 1. Registrar lançamento

O POST termina no commit local. A publicação é outro fluxo.

```mermaid
sequenceDiagram
  autonumber
  participant U as Comerciante
  participant CF as CloudFront
  participant G as API Gateway
  participant A as ALB interno
  participant L as lancamentos-service
  participant P as Postgres lancamentos
  U->>CF: POST /api/v1/lancamentos TLS no edge
  CF->>G: HTTPS, Host do execute-api, X-Origin-Verify
  G->>G: WAF regional, authorizer Cognito, validator
  G->>A: VPC Link v2, path sem o stage
  A->>L: encaminha com JWT
  L->>L: valida JWT de novo, escopo, claim e Dinheiro
  L->>P: BEGIN
  L->>P: INSERT lancamento
  L->>P: INSERT outbox com trace_parent e correlation_id
  L->>P: INSERT idempotencia
  P-->>L: COMMIT
  L-->>A: 201 e X-Correlation-Id
  A-->>G: 201
  G-->>CF: 201
  CF-->>U: 201
```

Se o header `Idempotency-Key` repetir a mesma requisição, o fluxo para no `SELECT` da idempotência e devolve 200, sem segundo insert.

Token inválido não chega na task:

```mermaid
sequenceDiagram
  autonumber
  participant U as Comerciante
  participant CF as CloudFront
  participant G as API Gateway
  U->>CF: POST /api/v1/lancamentos com Bearer invalido
  CF->>G: repassa Authorization e acrescenta X-Origin-Verify
  G->>G: authorizer Cognito recusa o token
  G-->>CF: 401
  CF-->>U: 401
```

O WAF regional responde 403 se o header `X-Origin-Verify` não for o atual nem o anterior. Esse caso é chamada direta ao `execute-api`, não o comerciante passando pelo CloudFront. O serviço continua validando o JWT de quem passou: o gateway não substitui o resource server.

## 2. Outbox relay

```mermaid
sequenceDiagram
  autonumber
  participant R as Relay
  participant P as Postgres lancamentos
  participant S as SNS fluxo-caixa-lancamentos-events
  participant Q as SQS consolidado-lancamentos-queue
  loop a cada 500 ms
    R->>P: SELECT lote 100 FOR UPDATE SKIP LOCKED
    P-->>R: linhas com publicado_em nulo
    R->>S: Publish JSON v1 e atributos eventType traceparent correlationId
    S->>Q: entrega raw message delivery
    S-->>R: message id
    R->>P: UPDATE publicado_em e COMMIT
  end
```

Falha no `Publish`: a transação do lote não marca `publicado_em`, `tentativas` sobe, o próximo ciclo tenta de novo. Duas tasks não pegam a mesma linha por causa do `SKIP LOCKED`. O POST da seção anterior já respondeu ao comerciante antes deste loop.

## 3. Consolidação

```mermaid
sequenceDiagram
  autonumber
  participant Q as SQS
  participant C as consolidado-service
  participant D as Postgres consolidado
  participant K as Redis
  C->>Q: ReceiveMessage long polling 20 s
  Q-->>C: corpo JSON cru
  C->>C: ACL traduz para AplicarLancamento
  C->>D: BEGIN
  C->>D: INSERT eventos_processados eventId
  alt eventId novo
    C->>D: UPSERT saldos_diarios da competencia
    C->>D: COMMIT
    C->>K: INCR versao do comerciante
    C->>Q: DeleteMessage
  else chave duplicada
    C->>D: COMMIT sem alterar o saldo
    C->>Q: DeleteMessage
  end
```

O `INCR` da versão acontece depois do commit do saldo. Se o Redis falhar nesse momento, a versão antiga continua válida até o TTL de 30 s. Se o commit do banco falhar, a mensagem não é apagada e volta após o visibility timeout.

## 4. Consulta com cache hit e cache miss

```mermaid
sequenceDiagram
  autonumber
  participant U as Comerciante
  participant C as consolidado-service
  participant K as Redis
  participant D as Postgres consolidado
  U->>C: GET /api/v1/consolidados/diario/2026-11-10
  C->>K: GET versao do comerciante
  K-->>C: versao 41
  C->>K: GET consolidado dia id versao data
  alt hit
    K-->>C: JSON do saldo
    C-->>U: 200
  else miss
    K-->>C: ausente
    C->>D: SELECT saldos_diarios
    C->>D: SELECT SUM saldo ate a data
    D-->>C: linha ou vazio
    C->>C: vazio vira zeros
    C->>K: SET com TTL 30 s
    C-->>U: 200
  end
```

O dia sem movimento não é miss eterno contra a tabela vazia: o serviço cacheia também a resposta zerada, para um cliente que insiste no mesmo dia não martelar o `SUM`.

## 5. Estorno

```mermaid
sequenceDiagram
  autonumber
  participant U as Comerciante
  participant L as lancamentos-service
  participant P as Postgres lancamentos
  participant C as consolidado-service
  participant D as Postgres consolidado
  U->>L: POST /api/v1/lancamentos/id/estornos
  L->>P: le o original do mesmo comerciante
  alt ja estornado ou o proprio e estorno
    L-->>U: 409 problem+json
  else valido
    L->>P: INSERT lancamento tipo oposto mesma data mesmo valor
    L->>P: INSERT outbox LancamentoRegistrado
    L-->>U: 201
    Note over L,C: relay e fila como nos fluxos 2 e 3
    C->>D: soma o tipo oposto no dia da competencia original
  end
```

O saldo do dia corrente só muda se a competência do original for o dia corrente. Estorno de um crédito de ontem corrige ontem.

## 6. Consolidado fora e a recuperação

```mermaid
sequenceDiagram
  autonumber
  participant U as Comerciante
  participant L as lancamentos-service
  participant P as Postgres lancamentos
  participant Q as SQS
  participant C as consolidado-service
  U->>L: POST lancamento
  L->>P: COMMIT lancamento e outbox
  L-->>U: 201
  Note over Q,C: consolidado-service sem task saudavel
  P-->>Q: relay continua publicando
  Note over Q: mensagens retidas ate 14 dias
  U->>L: novo POST
  L-->>U: 201
  Note over C: tasks voltam readiness ok
  C->>Q: consome o backlog
  C->>C: aplica em ordem de chegada sem depender dela
```

Durante a janela, `GET` no consolidado falha (entra no error budget de 99,5%). `POST` e `GET` de lançamentos seguem (SLO de 99,9%). Não há retry do cliente contra o consolidado embutido no POST.

Drenagem da premissa de 20 evt/s durante 1 h: 72.000 mensagens. Duas tasks a ~100 aplicações/s levam cerca de 6 minutos para alcançar o fim da fila. O p95 de 5 s vale para o regime estável, não para o catch-up depois de uma hora parado — o runbook trata esse catch-up como degradação esperada e medida pela idade da mensagem.

## 7. Reprocessar a DLQ

Uma mensagem chega na DLQ depois de 5 recebimentos. Causas típicas: JSON incompatível, `eventVersion` desconhecida, bug no `UPSERT`, poison message.

```mermaid
sequenceDiagram
  autonumber
  participant Op as Operacao
  participant DLQ as consolidado-lancamentos-dlq
  participant Q as consolidado-lancamentos-queue
  participant C as consolidado-service
  Op->>DLQ: inspeciona corpo e atributo
  alt bug corrigido e evento ainda valido
    Op->>Q: redrive da DLQ para a fila principal
    Q->>C: entrega
    C->>C: eventId novo aplica eventId conhecido ignora
  else evento invalido
    Op->>Op: descarta com registro e corrige o produtor
  end
```

Redrive é seguro por causa do `eventId`. Não se edita a mensagem para “fazer passar”. Se o contrato estiver errado, a correção é `eventVersion` nova e consumidor que a entende.

## 8. O que cada fluxo garante

| Fluxo | Garantia | O que não garante |
|---|---|---|
| POST | Durabilidade do lançamento no commit | Saldo já visível |
| Relay | Publicação eventual com retentativa | Latência fixa se o SNS estiver degradado |
| Consumo | Soma única por `eventId` | Ordem entre eventos |
| GET com cache | Leitura rápida da última geração cacheada | Leitura monotonicamente fresca em todo miss concorrente |
| Queda do consolidado | POST segue | GET do saldo segue |
| DLQ | A fila principal não trava num poison message | O saldo daquele evento, até o redrive |

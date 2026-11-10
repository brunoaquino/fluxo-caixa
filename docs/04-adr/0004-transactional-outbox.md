# Transactional outbox

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

O lançamento e o evento de integração precisam concordar. Se o banco confirma e o SNS não recebe, o saldo nunca vê o fato. Se o SNS recebe e o banco desfaz, o saldo soma um fato que não existe. Os dois sistemas não participam da mesma transação.

## Drivers

- Zero perda de evento depois de um 201.
- POST com p95 &lt; 200 ms, portanto o publish não pode ser obrigatório dentro do tempo de resposta.
- Duas réplicas do serviço publicando ao mesmo tempo, sem evento duplicado por corrida do relay.
- Volume da premissa: 20 escritas/s no pico, muito abaixo do que justificaria uma plataforma de CDC.

## Opções consideradas

### 1. Dual-write

`COMMIT` e, em seguida, `Publish`.

- Positivo: pouco código.
- Negativo: o processo pode morrer no meio. Retry do publish depois do commit exige exatamente a caixa de saída que esta opção finge não precisar. Publicar antes do commit entrega evento de transação abortada.

### 2. CDC com Debezium lendo o WAL do Postgres, publicando no SNS ou no Kafka

- Positivo: o domínio não conhece outbox. O log é a fonte. Bom quando há muitos agregados e vários consumidores de banco legado.
- Negativo: um conector para operar, em geral em Kafka Connect ou MSK Connect, com o custo e a complexidade que o [ADR 0005](0005-sns-sqs-mensageria.md) já recusou para a mensageria. Para uma tabela e um evento, é mecanismo demais. Também empurra o formato do evento para o formato da linha, a menos que um SMT o traduza — a tradução volta a ser código nosso.

### 3. Outbox na mesma transação e relay no processo

Tabela `outbox_eventos(id, aggregate_id, event_type, payload JSONB, criado_em, publicado_em, tentativas, proxima_tentativa_em)`. O caso de uso insere o lançamento e a linha, com `proxima_tentativa_em` igual ao instante da escrita. Um relay agenda a cada 500 ms, lê lote de 100 com `SELECT … FOR UPDATE SKIP LOCKED` das linhas ainda não publicadas cuja próxima tentativa já chegou, publica e marca `publicado_em`. Falha de publish incrementa `tentativas` e empurra `proxima_tentativa_em` com backoff exponencial até 60 s, sem Resilience4j.

- Positivo: o 201 só sai se o evento está durável. Falha do SNS atrasa a publicação e não desfaz o lançamento. `SKIP LOCKED` permite duas tasks. O payload é o JSON do contrato, não um dump da tabela.
- Negativo: polling (atraso médio de uma fração do intervalo de 500 ms, compatível com o p95 de 5 s de ponta a ponta). Tabela que cresce se o publish parar — precisa de alarme de idade e de limpeza das linhas já publicadas. Relógio dentro da API, que compete um pouco de CPU com o POST (irrelevante a 20 escritas/s).

## Decisão

Opção 3. O relay vive no `lancamentos-service`, não num terceiro deploy. Extrair um worker só se a métrica mostrar que o agendador atrapalha a API.

Publicação bem-sucedida atualiza `publicado_em` numa transação curta depois do ack do SNS. Se o processo cair depois do publish e antes do update, o evento é republicado. O consumidor idempotente ([ADR 0014](0014-idempotencia.md)) torna essa duplicata inofensiva.

## Consequências

### Positivas

- O RNF de durabilidade do lançamento e o de independência do consolidado ficam no mesmo mecanismo.
- O teste de integração sobe Postgres e LocalStack e prova o ciclo sem AWS de verdade.

### Negativas

- Relógio de 500 ms e lote de 100 são parâmetros operacionais. Mudá-los mexe no lag e na carga do banco. Ficam em configuração, com o alarme de outbox pendente do [documento 07](../07-observabilidade.md).
- A limpeza de linhas publicadas é tarefa de manutenção. Sem ela, a tabela cresce para sempre. Retenção sugerida: 7 dias depois de `publicado_em`, para auditoria curta do payload.

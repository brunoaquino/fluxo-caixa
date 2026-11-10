# Idempotência na escrita e no consumo

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

Há duas duplicatas de naturezas diferentes. O cliente do caixa pode repetir o POST porque a resposta se perdeu (rede, timeout do app). O SQS entrega a mesma mensagem mais de uma vez, e o relay pode republicar se cair depois do `Publish` e antes de marcar a outbox. As duas, se tratadas com “INSERT sempre”, criam dinheiro que não existiu.

## Drivers

- Repetir o POST com a mesma intenção devolve o mesmo lançamento.
- Reentrega do evento não altera o saldo duas vezes.
- A chave do cliente não vaza entre comerciantes.
- Retry com backoff só é seguro se o efeito for idempotente.

## Opções consideradas

### 1. Confiar em “o cliente não repete” e em FIFO com deduplicação de 5 minutos

- Positivo: menos tabela.
- Negativo: timeout no celular repete sim. A janela de deduplicação do FIFO não cobre redrive da DLQ no dia seguinte nem republicação da outbox. E o [ADR 0005](0005-sns-sqs-mensageria.md) não adotou FIFO.

### 2. Idempotência só no consumidor

- Positivo: protege o saldo, que é o agregado derivado.
- Negativo: o extrato fica com dois lançamentos iguais para uma venda. O caixa, que é a fonte da verdade, estaria errado. O consolidado estaria “certo” em cima de um fato duplicado.

### 3. Chave no POST e `eventId` no consumo

**HTTP.** Header opcional `Idempotency-Key` (UUID), único por `(comerciante_id, chave)`, no POST de lançamento e no POST de estorno. A coluna `idempotencia.impressao_digital` guarda o SHA-256 do corpo canônico (no registro: tipo, valor com duas casas, data, descrição e categoria; no estorno: id do lançamento e motivo). A resposta não é armazenada: a repetição relê o lançamento pelo `lancamento_id`.

| Situação | Resultado |
|---|---|
| Primeira vez | 201 e persiste |
| Mesma chave, mesma impressão | 200 e o mesmo lançamento |
| Mesma chave, impressão diferente | 409 |

Sem header, cada POST é um fato novo — o blueprint deixa a chave opcional. Cliente de PDV deve enviá-la; cliente de teste pode omitir.

**Consumo.** `eventos_processados(event_id primary key)` na mesma transação do `UPSERT` do saldo. Conflito de chave: a mensagem é descartada da fila e o saldo não muda.

- Positivo: as duas pontas do dinheiro ficam protegidas. Retry do relay, do SQS e do cliente deixam de ser incidente.
- Negativo: a obrigação de definir o que é “o mesmo corpo”. A impressão cobre o corpo canônico, não o header de autorização. A linha não expira nesta entrega: a mesma chave continua válida enquanto o registro existir. Limpeza é operação, não um TTL de 24 h no código.

## Decisão

Opção 3. A idempotência HTTP é por comerciante. Um tenant não bloqueia a chave de outro. O `eventId` é gerado na escrita (UUID) e vai no payload; o consumidor não deduplica por `lancamentoId` apenas, porque o contrato manda deduplicar por `eventId` — na prática os dois são 1:1 hoje, e usar o campo do contrato evita uma segunda regra.

## Consequências

### Positivas

- O teste de carga pode repetir request sem inventar lançamento.
- Redrive da DLQ é seguro.

### Negativas

- 200 na repetição e 201 na criação exigem que o cliente trate os dois como sucesso. Está no RF-04. Cliente que só aceita 201 vai marcar erro numa retentativa que deu certo.
- Hash instável (campo com espaço, escala `150.0` versus `150.00`) gera 409 legítimo e surpreende. A normalização do valor para duas casas acontece antes do hash.

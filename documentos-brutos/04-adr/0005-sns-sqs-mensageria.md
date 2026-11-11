# SNS e SQS como mensageria

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

O outbox precisa de um transporte entre o serviço de lançamentos e o de consolidado, com retenção se o consumidor cair, redrive para mensagem inválida e espaço para um segundo consumidor no futuro. A premissa de volume é 20 eventos/s no pico de escrita e cerca de 2 milhões de eventos por mês se a média for ~0,8 evt/s. O requisito de leitura (50 req/s) não passa pela fila.

## Drivers

- Retenção de muitos dias (o blueprint fixa 14 na fila).
- DLQ com `maxReceiveCount` 5 e visibility timeout de 60 s.
- Fan-out: um tópico, uma fila hoje, outra fila amanhã sem o produtor mudar.
- Custo e operação compatíveis com dois serviços, não com um cluster.
- LocalStack no ambiente local, SDK v2 na aplicação.
- Ordem total não é necessária: a soma é comutativa ([domínio](../02-dominio-ddd.md)).

## Opções consideradas

### 1. Amazon MSK (Kafka)

- Positivo: log retido, replay por offset, vários consumidores independentes, ecossistema de stream.
- Negativo: o menor cluster Multi-AZ são três brokers mais armazenamento, na casa de **centenas de USD por mês** mesmo em `us-east-1` (e mais caro em `sa-east-1`), duas ordens de grandeza acima da conta de SNS/SQS neste volume. Operação de versão, disco e partição. Local fica mais pesado que um LocalStack. Kafka se paga quando houver replay longo, processamento de stream ou throughput sustentado na casa de milhares de eventos/s — cenário do [roadmap](../10-evolucoes-futuras.md), não o de agora.

### 2. Amazon MQ (ActiveMQ ou RabbitMQ)

- Positivo: fila clássica, roteamento flexível, protocolo que muitos ERPs já falam.
- Negativo: broker para patchar, dimensionar e colocar em Multi-AZ (ativo/espera custa instância dobrada). Não é o encaixe natural do restante da conta, que já é API AWS. Mais caro e mais frágil que SQS para um único consumidor idempotente.

### 3. Amazon EventBridge

- Positivo: roteamento por regra, schema registry, baixo custo na casa de USD 1 por milhão de eventos.
- Negativo: o buffer de consumidor, a concorrência e a DLQ que queremos são do SQS. EventBridge na frente do SQS acrescenta um hop e uma conta sem uma regra de roteamento que o SNS já não faça com assinatura. Útil no dia em que dezenas de tipos de evento precisarem de filtros diferentes. Hoje existe um tipo.

### 4. SNS + SQS Standard + DLQ

Tópico `fluxo-caixa-lancamentos-events`, fila `consolidado-lancamentos-queue`, DLQ `consolidado-lancamentos-dlq`. Assinatura com raw message delivery. Fila Standard, não FIFO.

- Positivo: serviço gerenciado, retenção de 14 dias, long polling, competing consumers nas tasks do consolidado, custo de poucos dólares no volume da premissa (a linha está em [custos](../08-custos.md), abaixo de USD 10/mês). Segundo consumidor = segunda fila na mesma assinatura. LocalStack cobre o fluxo.
- Negativo: sem ordem e sem replay por offset. Mensagem consumida e apagada não volta, salvo retenção ainda não expirada ou redrive da DLQ. Payload máximo 256 KB — o evento v1 está muito abaixo. Duplicata é normal e tem de ser absorvida pelo consumidor.

FIFO foi descartado dentro desta opção. FIFO dá ordem por message group e deduplicação de 5 minutos, com teto de throughput por grupo. A deduplicação curta não substitui a tabela `eventos_processados` (o redrive acontece dias depois). A ordem não é requisito. Standard é o encaixe.

## Decisão

Opção 4, fila Standard, criptografia KMS, policy mínima por task role. Cliente AWS SDK for Java v2, sem Spring Cloud AWS, para não acoplar o ciclo de vida do framework ao binding da fila. O listener é código nosso no adaptador, pequeno o bastante para testar com LocalStack.

## Consequências

### Positivas

- O custo variável de mensageria é irrelevante perto de NAT, endpoint e RDS. A decisão não precisa ser revista por dinheiro até o volume subir ordens de grandeza.
- O teste de resiliência enche a fila com o consumidor parado e depois observa o catch-up.

### Negativas

- Sem log de eventos consultável por offset. Reprocessar história inteira exige reler a outbox ou o banco de lançamentos e republicar — procedimento de operação, não feature do ônibus. Se um dia o reprocessamento histórico for rotina, a opção 1 volta à mesa.
- Visibilidade e alarme passam a ser parte do desenho. Fila sem alarme de idade é evento perdido na prática, mesmo com retenção de 14 dias.

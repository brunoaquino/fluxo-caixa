# PostgreSQL no RDS, um banco por serviço

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

Os dois serviços persistem estado relacional com integridade (lançamento imutável, unicidade de estorno, saldo por UPSERT atômico). É preciso escolher o motor e o grau de isolamento: schema, database ou instância.

## Drivers

- Transação local forte na escrita e na aplicação do evento.
- `NUMERIC` para dinheiro, índice único parcial para estorno, `SKIP LOCKED` para o relay, JSONB para o payload da outbox.
- Falha de capacidade do consolidado (conexão, disco, IOPS) não pode impedir o INSERT de lançamento.
- RPO ≤ 5 min e recuperação pontual.
- Time que já opera SQL. Volume que cabe numa instância small (ver [capacidade](../01-contexto-e-requisitos.md#analise-do-rnf-critico)).

## Opções consideradas

### 1. Um RDS, dois databases

- Positivo: uma instância, uma janela de backup, metade do custo de compute Multi-AZ.
- Negativo: CPU, disco e `max_connections` são compartilhados. O isolamento que o RNF pede no processo se desfaz no banco. Serve para o Docker local e, se o orçamento apertar, para um dev AWS. Não serve para produção.

### 2. DynamoDB por serviço

- Positivo: escala horizontal gerenciada, sem conexão persistente.
- Negativo: o relay com `SKIP LOCKED`, a unicidade condicional do estorno e o `SUM` do acumulado viram desenho de chave e GSI. Transação de até 100 itens não é o problema; a perda é o modelo relacional que expressa as invariantes com pouco código. O time pagaria complexidade de modelagem num volume que não precisa dela.

### 3. PostgreSQL 17, instância RDS por serviço em produção

Dois bancos: `lancamentos` e `consolidado`. Em produção, duas instâncias Multi-AZ, criptografadas com KMS, senha no Secrets Manager, Flyway em cada serviço. Classe inicial `db.t4g.small` (2 GiB, ~225 conexões), porque o cálculo de Little dá menos de uma conexão ativa no pico e no máximo 60 reservadas no teto de tasks. gp3 50 GB.

- Positivo: SQL, integridade, PITR, failover Multi-AZ, motor maduro para `NUMERIC(19,2)`. Instância separada realiza o database-per-service de verdade. Small é suficiente e custa cerca de metade de medium nesta região (números no [documento 08](../08-custos.md)).
- Negativo: duas contas de RDS, o maior item variável junto com a rede. Multi-AZ dobra o preço da hora da instância e o GB do gp3 já vem precificado em dobro na tabela (`SAE1-RDS:Multi-AZ-GP3-Storage`). Operação de versão do Postgres em dois lugares.

## Decisão

Opção 3 em produção. No Docker local, um único Postgres cria os dois databases e os usuários `lancamentos` e `consolidado` — o isolamento de credencial existe, o de hardware não, e isso é aceitável na máquina do desenvolvedor. Dev AWS, se existir, usa duas instâncias `db.t4g.micro` Single-AZ para manter a fronteira de segredo sem pagar Multi-AZ.

Não há usuário de banco cruzado. Não há foreign key entre serviços. Não há dblink.

## Consequências

### Positivas

- A invariante “um estorno” é índice único, não só `if` no código.
- Dá para restaurar o consolidado de um PITR sem rebobinar a trilha de lançamentos, e o contrário.

### Negativas

- Relatório que precise de extrato e saldo no mesmo request faz duas chamadas HTTP. Não faz join. É o custo do isolamento e é o que a API já expressa.
- Subir de classe (small → medium) é a resposta a CPU credit ou memória, não a um pressentimento. O gatilho é métrica, documentado no runbook.

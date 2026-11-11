# Desempenho das consultas do consolidado

Medido em PostgreSQL 17 (imagem `postgres:17-alpine`), cache de buffers quente, sem concorrência. Volume sintético: **20 comerciantes × 1.097 dias** (2023-11-10 a 2026-11-10) = **21.940 linhas**. O script reproduzível está em [explain-saldos.sql](explain-saldos.sql). Os tempos absolutos são de um container local; o que vale levar para a RDS é o formato do plano.

A PK `(comerciante_id, data)` é o único índice. As duas consultas críticas usam essa chave. Não há N+1: o dia é uma agregação e o período é uma sentença.

## Saldo do dia e acumulado

Uma varredura `comerciante_id = ? AND data <= ?`, com `FILTER` para o movimento do dia e `SUM` para o acumulado.

```
Aggregate  (actual time=0.591..0.592 rows=1)
  -> Bitmap Heap Scan on saldos_diarios  (actual time=0.068..0.183 rows=1097)
       Heap Blocks: exact=12
       -> Bitmap Index Scan on pk_saldos_diarios  (actual time=0.058..0.058 rows=1097)
            Index Cond: (comerciante_id = ? AND data <= ?)
Planning Time: 0.643 ms
Execution Time: 0.674 ms
Buffers: shared hit=19
```

1.097 dias de um comerciante, 19 páginas em cache, **0,67 ms**.

## Relatório do período

Uma sentença: soma anterior (`data < inicio`) na PK, dias do intervalo na PK, `generate_series` para as lacunas e `SUM(...) OVER (ORDER BY data)` para o saldo acumulado. Período de 31 dias:

```
WindowAgg  (actual time=1.315..1.388 rows=31)
  -> Nested Loop
       -> Merge Left Join
            -> Index Scan using pk_saldos_diarios
                 Index Cond: (comerciante_id = ? AND data >= ? AND data <= ?)
                 (actual time=0.025..0.032 rows=31)
       -> Aggregate  (actual time=1.191..1.193 rows=1 loops=1)
            -> Bitmap Index Scan on pk_saldos_diarios
                 Index Cond: (comerciante_id = ? AND data < ?)
                 (rows=1066)
Planning Time: 0.460 ms
Execution Time: 1.607 ms
Buffers: shared hit=22
```

O intervalo de 31 dias é index scan direto (4 páginas). O custo dominante é a soma de tudo que veio antes (~1,2 ms para 1.066 dias). A window function em si ordena 31 linhas. **1,61 ms** no total.

## Pool e timeout

Por task (consolidado em prod é 1 vCPU): Hikari de escrita com no máximo 10 conexões, `connection-timeout` 3 s, `max-lifetime` 15 min, `keepalive` 5 min. Cada conexão nova executa `SET statement_timeout = '3s'`. As consultas medidas ficam duas a três ordens de grandeza abaixo desse teto; o timeout existe para cortar varredura acidental. Com réplica (`DB_READER_URL`), o pool de leitura repete o mesmo tamanho e é `readOnly`.

10 tasks × 10 conexões de escrita ≈ 100 no primário. Um `db.t4g.medium` comporta isso com folga, inclusive se a réplica estiver ligada.

## O que não fazer agora

O plano já é range scan na PK. Índice extra, partição ou saldo materializado não mudam o p95 enquanto o cache de 30 s absorve a leitura e o histórico por comerciante cabe em ~1.000 linhas.

## Quando o volume crescer

- **Índice de cobertura** `ON saldos_diarios (comerciante_id, data) INCLUDE (total_creditos, total_debitos, quantidade_lancamentos, atualizado_em)` se o `Bitmap Heap Scan` passar a dominar (hoje são 12 páginas). A PK não cobre as colunas somadas, então o plano busca o heap.
- **Snapshot mensal** (`comerciante_id`, `ano_mes`, `saldo_acumulado`) quando um comerciante passar de ~10 mil dias ou o `EXPLAIN` do acumulado passar de ~10 ms. A consulta do dia soma o snapshot do mês anterior mais os dias do mês corrente, em vez do histórico inteiro.
- **Réplica de leitura** (`DB_READER_URL`) quando o primário dividir UPSERT do consumidor com o `SUM` das consultas. O roteamento já manda `@Transactional(readOnly = true)` para a réplica.
- **Particionamento** por mês não ajuda: a PK começa por `comerciante_id`, não por data. Hash de `comerciante_id` só faria sentido com dezenas de milhões de linhas.

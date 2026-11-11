# Desempenho das consultas — lancamentos-service

Medido com `EXPLAIN (ANALYZE, BUFFERS)` no PostgreSQL 17 do Testcontainers, depois de `ANALYZE`.
Volume da amostra: 4.000 lançamentos de um comerciante em 10 datas (cerca de 400 no dia consultado) e, no outbox, 1.500 pendentes fora da janela mais 30 dentro da janela do relay.

O `ORDER BY criado_em ASC` da listagem percorre o índice `(comerciante_id, data_lancamento, criado_em DESC)` de trás para frente. A contagem da página usa o mesmo índice. O relay e a limpeza usam os índices parciais.

Paginação por offset fica limitada a `page <= 1000` e `size <= 100`. Keyset (`criado_em`, `id`) e particionamento mensal de `lancamentos` ficam como evolução se o offset passar a dominar o plano.

## Listagem

```
Limit  (cost=0.28..18.05 rows=50 width=95) (actual time=0.016..0.037 rows=50 loops=1)
  Buffers: shared hit=10
  ->  Index Scan Backward using idx_lancamentos_comerciante_data_criado on lancamentos  (cost=0.28..142.41 rows=400 width=95) (actual time=0.015..0.031 rows=50 loops=1)
        Index Cond: ((comerciante_id = '...'::uuid) AND (data_lancamento = '2026-11-10'::date))
        Buffers: shared hit=10
Planning Time: 0.560 ms
Execution Time: 0.055 ms
```

## Contagem da página

```
Aggregate  (cost=93.38..93.39 rows=1 width=8) (actual time=0.305..0.306 rows=1 loops=1)
  Buffers: shared hit=66
  ->  Bitmap Heap Scan on lancamentos  (cost=24.38..92.38 rows=400 width=0) (actual time=0.050..0.259 rows=400 loops=1)
        Recheck Cond: ((comerciante_id = '...'::uuid) AND (data_lancamento = '2026-11-10'::date))
        Heap Blocks: exact=62
        Buffers: shared hit=66
        ->  Bitmap Index Scan on idx_lancamentos_comerciante_data_criado  (cost=0.00..24.28 rows=400 width=0) (actual time=0.038..0.038 rows=400 loops=1)
              Buffers: shared hit=4
Planning Time: 0.065 ms
Execution Time: 0.344 ms
```

## Relay da outbox

`SELECT ... FOR UPDATE SKIP LOCKED` com faixa em `proxima_tentativa_em` (a carga de teste fica no futuro para o relay real, que usa `now()`, não publicar esses registros). 30 linhas entram no índice parcial; as outras 1.500 ficam de fora do predicado.

```
Limit  (cost=33.30..33.67 rows=30 width=207) (actual time=0.050..0.072 rows=30 loops=1)
  Buffers: shared hit=33
  ->  LockRows  (cost=33.30..33.67 rows=30 width=207) (actual time=0.050..0.069 rows=30 loops=1)
        ->  Sort  (cost=33.30..33.37 rows=30 width=207) (actual time=0.041..0.043 rows=30 loops=1)
              Sort Key: criado_em
              Sort Method: quicksort  Memory: 28kB
              ->  Bitmap Heap Scan on outbox_eventos  (cost=4.58..32.56 rows=30 width=207) (actual time=0.017..0.026 rows=30 loops=1)
                    Recheck Cond: ((proxima_tentativa_em >= ...) AND (proxima_tentativa_em <= ...) AND (publicado_em IS NULL))
                    Heap Blocks: exact=1
                    ->  Bitmap Index Scan on idx_outbox_eventos_pendentes  (cost=0.00..4.58 rows=30 width=0) (actual time=0.007..0.007 rows=30 loops=1)
Planning Time: 0.419 ms
Execution Time: 0.104 ms
```

O sort de 30 linhas cabe em memória. Com a janela do relay (`proxima_tentativa_em <= now()`) o mesmo índice parcial atende o filtro.

## Limpeza dos publicados

```
Delete on outbox_eventos  (cost=0.13..8.15 rows=0 width=0) (actual time=0.041..0.042 rows=0 loops=1)
  ->  Index Scan using idx_outbox_eventos_publicados on outbox_eventos  (cost=0.13..8.15 rows=1 width=6) (actual time=0.040..0.040 rows=0 loops=1)
        Index Cond: ((publicado_em IS NOT NULL) AND (publicado_em < ...))
Planning Time: 0.076 ms
Execution Time: 0.066 ms
```

## O que saiu do esquema

`idx_lancamentos_comerciante_data` era prefixo de `idx_lancamentos_comerciante_data_criado` e foi removido na V3. O índice parcial dos pendentes da V1 foi recriado na V3 com a mesma definição.

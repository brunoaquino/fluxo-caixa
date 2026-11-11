-- Volume sintético e EXPLAIN (ANALYZE, BUFFERS) das consultas do consolidado.
-- Espelha RepositorioConsolidadoJdbc: dia (range na PK) e período (window function).

CREATE TABLE IF NOT EXISTS saldos_diarios (
    comerciante_id UUID NOT NULL,
    data DATE NOT NULL,
    total_creditos NUMERIC(19, 2) NOT NULL,
    total_debitos NUMERIC(19, 2) NOT NULL,
    quantidade_lancamentos INTEGER NOT NULL,
    versao BIGINT NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_saldos_diarios PRIMARY KEY (comerciante_id, data)
);

TRUNCATE saldos_diarios;

INSERT INTO saldos_diarios (comerciante_id, data, total_creditos, total_debitos, quantidade_lancamentos, versao, atualizado_em)
SELECT
    ('00000000-0000-4000-8000-' || lpad(m::text, 12, '0'))::uuid,
    d::date,
    100.00,
    40.00,
    2,
    1,
    TIMESTAMPTZ '2026-11-10 12:00:00+00'
FROM generate_series(1, 20) AS m
CROSS JOIN generate_series(DATE '2023-11-10', DATE '2026-11-10', INTERVAL '1 day') AS d;

ANALYZE saldos_diarios;

\echo '--- dia: 1 comerciante, soma até a data (PK range) ---'
EXPLAIN (ANALYZE, BUFFERS)
SELECT
    COALESCE(SUM(total_creditos) FILTER (WHERE data = DATE '2026-11-10'), 0) AS total_creditos,
    COALESCE(SUM(total_debitos) FILTER (WHERE data = DATE '2026-11-10'), 0) AS total_debitos,
    COALESCE(SUM(quantidade_lancamentos) FILTER (WHERE data = DATE '2026-11-10'), 0) AS quantidade_lancamentos,
    MAX(atualizado_em) FILTER (WHERE data = DATE '2026-11-10') AS atualizado_em,
    COALESCE(SUM(total_creditos - total_debitos), 0) AS saldo_acumulado
FROM saldos_diarios
WHERE comerciante_id = '00000000-0000-4000-8000-000000000001'::uuid
  AND data <= DATE '2026-11-10';

\echo '--- período: window + generate_series, 31 dias ---'
EXPLAIN (ANALYZE, BUFFERS)
WITH abertura AS (
    SELECT COALESCE(SUM(total_creditos - total_debitos), 0) AS acumulado
    FROM saldos_diarios
    WHERE comerciante_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND data < DATE '2026-10-11'
),
calendario AS (
    SELECT generate_series(DATE '2026-10-11', DATE '2026-11-10', INTERVAL '1 day')::date AS data
),
movimentos AS (
    SELECT data, total_creditos, total_debitos, quantidade_lancamentos, atualizado_em
    FROM saldos_diarios
    WHERE comerciante_id = '00000000-0000-4000-8000-000000000001'::uuid
      AND data BETWEEN DATE '2026-10-11' AND DATE '2026-11-10'
)
SELECT
    c.data,
    COALESCE(m.total_creditos, 0) AS total_creditos,
    COALESCE(m.total_debitos, 0) AS total_debitos,
    COALESCE(m.quantidade_lancamentos, 0) AS quantidade_lancamentos,
    m.atualizado_em,
    a.acumulado
        + SUM(COALESCE(m.total_creditos, 0) - COALESCE(m.total_debitos, 0))
            OVER (ORDER BY c.data) AS saldo_acumulado
FROM calendario c
CROSS JOIN abertura a
LEFT JOIN movimentos m ON m.data = c.data
ORDER BY c.data;

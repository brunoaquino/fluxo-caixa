-- Projeção materializada do saldo diário e trilha de idempotência do consumidor.
-- A PK (comerciante_id, data) cobre a leitura do dia e o range do saldo acumulado.

CREATE TABLE saldos_diarios (
    comerciante_id UUID NOT NULL,
    data DATE NOT NULL,
    total_creditos NUMERIC(19, 2) NOT NULL,
    total_debitos NUMERIC(19, 2) NOT NULL,
    quantidade_lancamentos INTEGER NOT NULL,
    versao BIGINT NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_saldos_diarios PRIMARY KEY (comerciante_id, data),
    CONSTRAINT ck_saldos_creditos_nao_negativo CHECK (total_creditos >= 0),
    CONSTRAINT ck_saldos_debitos_nao_negativo CHECK (total_debitos >= 0),
    CONSTRAINT ck_saldos_quantidade_nao_negativa CHECK (quantidade_lancamentos >= 0)
);

CREATE TABLE eventos_processados (
    event_id UUID NOT NULL,
    processado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_eventos_processados PRIMARY KEY (event_id)
);

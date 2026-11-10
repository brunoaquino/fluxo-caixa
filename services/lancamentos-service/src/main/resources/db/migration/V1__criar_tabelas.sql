-- Esquema do contexto de lançamentos. A correção é sempre um novo lançamento de estorno.
CREATE TABLE lancamentos (
    id UUID PRIMARY KEY,
    comerciante_id UUID NOT NULL,
    tipo VARCHAR(10) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    moeda VARCHAR(3) NOT NULL,
    data_lancamento DATE NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    categoria VARCHAR(60),
    lancamento_estornado_id UUID,
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_lancamentos_tipo CHECK (tipo IN ('CREDITO', 'DEBITO')),
    CONSTRAINT ck_lancamentos_valor_positivo CHECK (valor > 0),
    CONSTRAINT ck_lancamentos_moeda_brl CHECK (moeda = 'BRL'),
    CONSTRAINT ck_lancamentos_descricao CHECK (char_length(descricao) BETWEEN 1 AND 255),
    CONSTRAINT ck_lancamentos_estorno_nao_reflexivo CHECK (
        lancamento_estornado_id IS NULL OR lancamento_estornado_id <> id
    ),
    CONSTRAINT fk_lancamentos_estornado FOREIGN KEY (lancamento_estornado_id) REFERENCES lancamentos (id),
    CONSTRAINT uq_lancamentos_estorno UNIQUE (lancamento_estornado_id)
);

CREATE INDEX idx_lancamentos_comerciante_data
    ON lancamentos (comerciante_id, data_lancamento);

-- impressao_digital detecta reutilização da mesma chave com outro corpo.
CREATE TABLE idempotencia (
    chave UUID NOT NULL,
    comerciante_id UUID NOT NULL,
    lancamento_id UUID NOT NULL,
    impressao_digital VARCHAR(64) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_idempotencia PRIMARY KEY (chave, comerciante_id),
    CONSTRAINT fk_idempotencia_lancamento FOREIGN KEY (lancamento_id) REFERENCES lancamentos (id)
);

-- proxima_tentativa_em agenda o backoff do relay sem republicar em loop.
CREATE TABLE outbox_eventos (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    payload JSONB NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    publicado_em TIMESTAMPTZ,
    tentativas INTEGER NOT NULL DEFAULT 0,
    proxima_tentativa_em TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_outbox_tentativas CHECK (tentativas >= 0)
);

CREATE INDEX idx_outbox_eventos_pendentes
    ON outbox_eventos (proxima_tentativa_em, criado_em)
    WHERE publicado_em IS NULL;

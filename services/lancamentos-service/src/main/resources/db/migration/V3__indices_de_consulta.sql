-- Listagem por comerciante e data, ordenada por criação.
-- O prefixo (comerciante_id, data_lancamento) torna idx_lancamentos_comerciante_data redundante.
CREATE INDEX idx_lancamentos_comerciante_data_criado
    ON lancamentos (comerciante_id, data_lancamento, criado_em DESC);

DROP INDEX idx_lancamentos_comerciante_data;

-- Relay: pendentes prontos para publicação. A V1 já tinha esta forma;
-- recriamos aqui para o índice parcial ficar explícito junto da limpeza.
DROP INDEX idx_outbox_eventos_pendentes;

CREATE INDEX idx_outbox_eventos_pendentes
    ON outbox_eventos (proxima_tentativa_em, criado_em)
    WHERE publicado_em IS NULL;

-- Job de retenção dos eventos já publicados.
CREATE INDEX idx_outbox_eventos_publicados
    ON outbox_eventos (publicado_em)
    WHERE publicado_em IS NOT NULL;

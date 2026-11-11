-- Contexto do POST que originou o evento, para o relay reconstituir o trace.
ALTER TABLE outbox_eventos
    ADD COLUMN trace_parent VARCHAR(55),
    ADD COLUMN trace_state VARCHAR(512),
    ADD COLUMN correlation_id VARCHAR(36);

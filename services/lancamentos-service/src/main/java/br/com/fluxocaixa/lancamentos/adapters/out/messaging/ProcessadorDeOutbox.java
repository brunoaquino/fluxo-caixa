package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import br.com.fluxocaixa.lancamentos.application.port.out.Relogio;
import br.com.fluxocaixa.lancamentos.config.PropriedadesOutbox;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Publica um lote da outbox na mesma transação do bloqueio
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}, para várias réplicas não
 * enviarem o mesmo evento ao mesmo tempo.
 */
@Component
public class ProcessadorDeOutbox {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDeOutbox.class);

    private final JdbcTemplate jdbc;
    private final SnsPublicador publicador;
    private final Relogio relogio;
    private final PropriedadesOutbox propriedades;
    private final Counter publicados;
    private final Counter falhas;

    public ProcessadorDeOutbox(
            JdbcTemplate jdbc,
            SnsPublicador publicador,
            Relogio relogio,
            PropriedadesOutbox propriedades,
            MeterRegistry meterRegistry
    ) {
        this.jdbc = jdbc;
        this.publicador = publicador;
        this.relogio = relogio;
        this.propriedades = propriedades;
        this.publicados = Counter.builder("lancamentos.outbox.publicados")
                .description("Eventos da outbox publicados no SNS")
                .register(meterRegistry);
        this.falhas = Counter.builder("lancamentos.outbox.falhas")
                .description("Falhas ao publicar eventos da outbox")
                .register(meterRegistry);
    }

    @Transactional
    public void processarLote() {
        log.debug("Consultando outbox pendente lote={}", propriedades.lote());
        var pendentes = jdbc.query(
                """
                SELECT id, event_type, payload::text AS payload, tentativas
                FROM outbox_eventos
                WHERE publicado_em IS NULL
                  AND proxima_tentativa_em <= ?
                ORDER BY criado_em
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, linha) -> new EventoPendente(
                        rs.getObject("id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getInt("tentativas")
                ),
                Timestamp.from(relogio.agora()),
                propriedades.lote()
        );
        for (EventoPendente evento : pendentes) {
            publicar(evento);
        }
    }

    private void publicar(EventoPendente evento) {
        try {
            publicador.publicar(evento.payload(), evento.eventType());
            jdbc.update(
                    "UPDATE outbox_eventos SET publicado_em = ? WHERE id = ?",
                    Timestamp.from(relogio.agora()),
                    evento.id()
            );
            publicados.increment();
            log.info("Evento de outbox publicado id={}", evento.id());
        } catch (RuntimeException ex) {
            Instant proxima = PoliticaDeRetentativa.proxima(
                    evento.tentativas(),
                    relogio.agora(),
                    propriedades.backoffMaximoSegundos()
            );
            jdbc.update(
                    """
                    UPDATE outbox_eventos
                    SET tentativas = tentativas + 1,
                        proxima_tentativa_em = ?
                    WHERE id = ?
                    """,
                    Timestamp.from(proxima),
                    evento.id()
            );
            falhas.increment();
            log.warn("Falha ao publicar evento de outbox id={} tentativa={}", evento.id(), evento.tentativas() + 1, ex);
        }
    }

    private record EventoPendente(UUID id, String eventType, String payload, int tentativas) {
    }
}

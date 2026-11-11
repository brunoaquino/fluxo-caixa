package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import br.com.fluxocaixa.lancamentos.application.port.out.Relogio;
import br.com.fluxocaixa.lancamentos.config.PropriedadesOutbox;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publica um lote da outbox na mesma transação do bloqueio
 * {@code SELECT ... FOR UPDATE SKIP LOCKED}. Com o circuito aberto o lote
 * inteiro é adiado e continua na tabela.
 */
@Component
public class ProcessadorDeOutbox {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDeOutbox.class);

    private final JdbcTemplate jdbc;
    private final SnsPublicador publicador;
    private final RastreadorDePublicacao rastreador;
    private final Relogio relogio;
    private final PropriedadesOutbox propriedades;
    private final CircuitBreakerRegistry circuitos;
    private final JsonMapper jsonMapper;
    private final Counter publicados;
    private final Counter falhas;
    private final Counter adiados;

    public ProcessadorDeOutbox(
            JdbcTemplate jdbc,
            SnsPublicador publicador,
            RastreadorDePublicacao rastreador,
            Relogio relogio,
            PropriedadesOutbox propriedades,
            CircuitBreakerRegistry circuitos,
            JsonMapper jsonMapper,
            MeterRegistry meterRegistry
    ) {
        this.jdbc = jdbc;
        this.publicador = publicador;
        this.rastreador = rastreador;
        this.relogio = relogio;
        this.propriedades = propriedades;
        this.circuitos = circuitos;
        this.jsonMapper = jsonMapper;
        this.publicados = Counter.builder("lancamentos.outbox.publicados")
                .description("Eventos da outbox publicados no SNS")
                .register(meterRegistry);
        this.falhas = Counter.builder("lancamentos.outbox.falhas")
                .description("Falhas ao publicar eventos da outbox")
                .register(meterRegistry);
        this.adiados = Counter.builder("lancamentos.outbox.adiados")
                .description("Eventos adiados porque o circuito do SNS está aberto")
                .register(meterRegistry);
    }

    @Transactional
    public void processarLote() {
        List<EventoPendente> pendentes = jdbc.query(
                """
                SELECT id, aggregate_id, event_type, payload::text AS payload, tentativas,
                       trace_parent, trace_state, correlation_id
                FROM outbox_eventos
                WHERE publicado_em IS NULL
                  AND proxima_tentativa_em <= ?
                ORDER BY criado_em
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, linha) -> new EventoPendente(
                        rs.getObject("id", UUID.class),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getInt("tentativas"),
                        rs.getString("trace_parent"),
                        rs.getString("trace_state"),
                        rs.getString("correlation_id")
                ),
                Timestamp.from(relogio.agora()),
                propriedades.lote()
        );
        if (pendentes.isEmpty()) {
            return;
        }
        if (circuitos.circuitBreaker(SnsPublicador.INSTANCIA).getState() == CircuitBreaker.State.OPEN) {
            adiar(pendentes);
            log.warn("Circuito {} aberto; lote de {} evento(s) permanece na outbox", SnsPublicador.INSTANCIA, pendentes.size());
            return;
        }
        for (int indice = 0; indice < pendentes.size(); indice++) {
            try {
                publicar(pendentes.get(indice));
            } catch (CallNotPermittedException ex) {
                adiar(pendentes.subList(indice, pendentes.size()));
                log.warn("Circuito {} abriu durante o lote; {} evento(s) adiados", SnsPublicador.INSTANCIA, pendentes.size() - indice);
                return;
            }
        }
    }

    private void publicar(EventoPendente evento) {
        try (RastreadorDePublicacao.Escopo escopo = rastreador.abrir(evento.paraRastreio(comercianteDe(evento.payload())))) {
            try {
                publicador.publicar(evento.payload(), evento.eventType(), escopo.atributos());
                jdbc.update(
                        "UPDATE outbox_eventos SET publicado_em = ? WHERE id = ?",
                        Timestamp.from(relogio.agora()),
                        evento.id()
                );
                publicados.increment();
                log.info("Evento de outbox publicado");
            } catch (CallNotPermittedException ex) {
                escopo.erro(ex);
                throw ex;
            } catch (RuntimeException ex) {
                escopo.erro(ex);
                reagendar(evento);
            }
        }
    }

    private void reagendar(EventoPendente evento) {
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
        log.warn("Falha ao publicar evento de outbox tentativa={}", evento.tentativas() + 1);
    }

    private void adiar(List<EventoPendente> eventos) {
        Instant proxima = relogio.agora().plus(propriedades.adiamentoCircuito());
        for (EventoPendente evento : eventos) {
            jdbc.update(
                    "UPDATE outbox_eventos SET proxima_tentativa_em = ? WHERE id = ?",
                    Timestamp.from(proxima),
                    evento.id()
            );
            adiados.increment();
        }
    }

    private String comercianteDe(String payload) {
        try {
            JsonNode no = jsonMapper.readTree(payload);
            JsonNode comerciante = no.get("comercianteId");
            if (comerciante == null || comerciante.isNull()) {
                return null;
            }
            return comerciante.asString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private record EventoPendente(
            UUID id,
            UUID aggregateId,
            String eventType,
            String payload,
            int tentativas,
            String traceParent,
            String traceState,
            String correlationId
    ) {
        private RastreadorDePublicacao.EventoRastreavel paraRastreio(String comercianteId) {
            return new RastreadorDePublicacao.EventoRastreavel(
                    id, aggregateId, traceParent, traceState, correlationId, comercianteId
            );
        }
    }
}

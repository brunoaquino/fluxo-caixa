package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MetricasOutbox {

    public MetricasOutbox(MeterRegistry meterRegistry, JdbcTemplate jdbc) {
        Gauge.builder("lancamentos.outbox.pendentes", jdbc, MetricasOutbox::contarPendentes)
                .description("Eventos da outbox ainda não publicados")
                .register(meterRegistry);
    }

    private static double contarPendentes(JdbcTemplate jdbc) {
        try {
            Long total = jdbc.queryForObject(
                    "SELECT count(*) FROM outbox_eventos WHERE publicado_em IS NULL",
                    Long.class
            );
            return total == null ? 0d : total.doubleValue();
        } catch (DataAccessException ex) {
            return 0d;
        }
    }
}

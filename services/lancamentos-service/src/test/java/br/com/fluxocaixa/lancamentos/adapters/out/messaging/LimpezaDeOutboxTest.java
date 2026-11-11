package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import br.com.fluxocaixa.lancamentos.application.port.out.Relogio;
import br.com.fluxocaixa.lancamentos.config.PropriedadesOutbox;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LimpezaDeOutboxTest {

    @Test
    void apagaSomentePublicadosMaisVelhosQueARetencao() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Relogio relogio = () -> Instant.parse("2026-11-10T03:00:00Z");
        when(jdbc.update(any(String.class), any(Timestamp.class))).thenReturn(4);
        var limpeza = new LimpezaDeOutbox(
                jdbc,
                relogio,
                new PropriedadesOutbox(500, 100, 60, Duration.ofDays(7), Duration.ofSeconds(30))
        );

        assertThat(limpeza.limpar()).isEqualTo(4);

        verify(jdbc).update(contains("publicado_em IS NOT NULL"), eq(Timestamp.from(Instant.parse("2026-11-03T03:00:00Z"))));
    }
}

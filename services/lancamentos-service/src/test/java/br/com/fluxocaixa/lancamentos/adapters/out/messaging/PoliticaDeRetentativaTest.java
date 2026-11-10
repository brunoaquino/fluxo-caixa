package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PoliticaDeRetentativaTest {

    @Test
    void dobraOIntervaloAteOTeto() {
        Instant agora = Instant.parse("2026-11-10T18:00:00Z");

        assertThat(PoliticaDeRetentativa.proxima(0, agora, 60)).isEqualTo(agora.plusSeconds(2));
        assertThat(PoliticaDeRetentativa.proxima(2, agora, 60)).isEqualTo(agora.plusSeconds(8));
        assertThat(PoliticaDeRetentativa.proxima(10, agora, 60)).isEqualTo(agora.plusSeconds(60));
    }
}

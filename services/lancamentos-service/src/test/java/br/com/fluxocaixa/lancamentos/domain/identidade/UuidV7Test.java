package br.com.fluxocaixa.lancamentos.domain.identidade;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

    @Test
    void geraUuidVersao7ComVarianteIetf() {
        UUID id = UuidV7.gerar();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }
}

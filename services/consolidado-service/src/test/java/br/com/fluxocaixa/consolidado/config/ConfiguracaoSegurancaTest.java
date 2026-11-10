package br.com.fluxocaixa.consolidado.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracaoSegurancaTest {

    @Test
    void montaAutoridadeComPrefixoConfiguravel() {
        assertThat(ConfiguracaoSeguranca.autoridadeLeitura(null)).isEqualTo("SCOPE_consolidado:read");
        assertThat(ConfiguracaoSeguranca.autoridadeLeitura("")).isEqualTo("SCOPE_consolidado:read");
        assertThat(ConfiguracaoSeguranca.autoridadeLeitura("fluxo-caixa/"))
                .isEqualTo("SCOPE_fluxo-caixa/consolidado:read");
    }
}

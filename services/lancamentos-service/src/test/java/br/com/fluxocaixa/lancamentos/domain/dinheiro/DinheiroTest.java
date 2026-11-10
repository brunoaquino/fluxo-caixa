package br.com.fluxocaixa.lancamentos.domain.dinheiro;

import br.com.fluxocaixa.lancamentos.domain.excecao.ValorMonetarioInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DinheiroTest {

    @Test
    void normalizaEscalaEFormataComDuasCasas() {
        assertThat(new Dinheiro(new BigDecimal("150.00")).formatado()).isEqualTo("150.00");
        assertThat(new Dinheiro(new BigDecimal("150.1")).formatado()).isEqualTo("150.10");
        assertThat(new Dinheiro(new BigDecimal("150")).formatado()).isEqualTo("150.00");
        assertThat(new Dinheiro(new BigDecimal("150.000")).formatado()).isEqualTo("150.00");
    }

    @Test
    void rejeitaValorInvalido() {
        assertThatThrownBy(() -> new Dinheiro(null))
                .isInstanceOf(ValorMonetarioInvalidoException.class);
        assertThatThrownBy(() -> new Dinheiro(BigDecimal.ZERO))
                .isInstanceOf(ValorMonetarioInvalidoException.class)
                .hasMessageContaining("maior que zero");
        assertThatThrownBy(() -> new Dinheiro(new BigDecimal("-1.00")))
                .isInstanceOf(ValorMonetarioInvalidoException.class);
        assertThatThrownBy(() -> new Dinheiro(new BigDecimal("10.001")))
                .isInstanceOf(ValorMonetarioInvalidoException.class)
                .hasMessageContaining("2 casas");
        assertThatThrownBy(() -> new Dinheiro(new BigDecimal("100000000000000000.00")))
                .isInstanceOf(ValorMonetarioInvalidoException.class)
                .hasMessageContaining("17 dígitos");
    }
}

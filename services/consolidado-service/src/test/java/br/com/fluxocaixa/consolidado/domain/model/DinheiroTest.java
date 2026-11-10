package br.com.fluxocaixa.consolidado.domain.model;

import br.com.fluxocaixa.consolidado.domain.exception.DinheiroInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DinheiroTest {

    @Test
    void normalizaValorPositivoComAteDuasCasas() {
        assertThat(Dinheiro.de("150.5").quantia()).isEqualByComparingTo("150.50");
        assertThat(Dinheiro.de("10").quantia()).isEqualByComparingTo("10.00");
        assertThat(Dinheiro.de(" 20.00 ").somar(Dinheiro.de("1.25")).quantia()).isEqualByComparingTo("21.25");
        assertThat(Dinheiro.MOEDA).isEqualTo("BRL");
    }

    @Test
    void rejeitaValorInvalido() {
        assertThatThrownBy(() -> Dinheiro.de("0")).isInstanceOf(DinheiroInvalidoException.class);
        assertThatThrownBy(() -> Dinheiro.de("-1.00")).isInstanceOf(DinheiroInvalidoException.class);
        assertThatThrownBy(() -> Dinheiro.de("1.234")).isInstanceOf(DinheiroInvalidoException.class);
        assertThatThrownBy(() -> Dinheiro.de("abc")).isInstanceOf(DinheiroInvalidoException.class);
        assertThatThrownBy(() -> Dinheiro.de(" ")).isInstanceOf(DinheiroInvalidoException.class);
        assertThatThrownBy(() -> Dinheiro.de(null)).isInstanceOf(DinheiroInvalidoException.class);
        assertThatThrownBy(() -> new Dinheiro(null)).isInstanceOf(DinheiroInvalidoException.class);
        assertThatThrownBy(() -> new Dinheiro(new BigDecimal("10.999"))).isInstanceOf(DinheiroInvalidoException.class);
    }
}

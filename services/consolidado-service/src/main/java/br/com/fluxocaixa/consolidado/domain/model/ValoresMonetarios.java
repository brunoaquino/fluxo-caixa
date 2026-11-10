package br.com.fluxocaixa.consolidado.domain.model;

import br.com.fluxocaixa.consolidado.domain.exception.DinheiroInvalidoException;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class ValoresMonetarios {

    public static final BigDecimal ZERO = new BigDecimal("0.00");

    private ValoresMonetarios() {
    }

    public static BigDecimal exato(BigDecimal valor) {
        if (valor == null) {
            throw new DinheiroInvalidoException("Valor monetário é obrigatório");
        }
        try {
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new DinheiroInvalidoException("Valor monetário deve ter no máximo 2 casas decimais");
        }
    }

    public static BigDecimal comDuasCasas(BigDecimal valor) {
        if (valor == null) {
            return ZERO;
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}

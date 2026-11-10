package br.com.fluxocaixa.lancamentos.domain.dinheiro;

import br.com.fluxocaixa.lancamentos.domain.excecao.ValorMonetarioInvalidoException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Valor monetário positivo em real, com exatamente duas casas decimais.
 */
public record Dinheiro(BigDecimal valor) {

    public static final String MOEDA = "BRL";
    public static final int ESCALA = 2;
    private static final int DIGITOS_INTEIROS = 17;

    public Dinheiro {
        if (valor == null) {
            throw new ValorMonetarioInvalidoException("Valor é obrigatório");
        }
        try {
            valor = valor.setScale(ESCALA, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ValorMonetarioInvalidoException("Valor deve ter no máximo 2 casas decimais");
        }
        if (valor.signum() <= 0) {
            throw new ValorMonetarioInvalidoException("Valor deve ser maior que zero");
        }
        int digitosInteiros = valor.precision() - valor.scale();
        if (digitosInteiros > DIGITOS_INTEIROS) {
            throw new ValorMonetarioInvalidoException("Valor excede o limite de 17 dígitos inteiros");
        }
    }

    public String formatado() {
        return valor.toPlainString();
    }
}

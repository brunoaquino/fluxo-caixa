package br.com.fluxocaixa.consolidado.domain.model;

import br.com.fluxocaixa.consolidado.domain.exception.DinheiroInvalidoException;

import java.math.BigDecimal;

/**
 * Valor monetário positivo em BRL, com no máximo duas casas decimais.
 */
public record Dinheiro(BigDecimal quantia) {

    public static final String MOEDA = "BRL";

    public Dinheiro {
        quantia = ValoresMonetarios.exato(quantia);
        if (quantia.signum() <= 0) {
            throw new DinheiroInvalidoException("Valor monetário deve ser maior que zero");
        }
    }

    public static Dinheiro de(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new DinheiroInvalidoException("Valor monetário é obrigatório");
        }
        try {
            return new Dinheiro(new BigDecimal(valor.trim()));
        } catch (NumberFormatException ex) {
            throw new DinheiroInvalidoException("Valor monetário inválido");
        }
    }

    public Dinheiro somar(Dinheiro outro) {
        return new Dinheiro(quantia.add(outro.quantia));
    }
}

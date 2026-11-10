package br.com.fluxocaixa.consolidado.domain.model;

import java.math.BigDecimal;

/**
 * Delta comutativo aplicado ao saldo do dia. Crédito e débito só somam;
 * estorno chega como um lançamento novo do tipo oposto.
 */
public record EfeitoLancamento(BigDecimal totalCreditos, BigDecimal totalDebitos, int quantidade) {

    public EfeitoLancamento {
        totalCreditos = ValoresMonetarios.exato(totalCreditos);
        totalDebitos = ValoresMonetarios.exato(totalDebitos);
        if (totalCreditos.signum() < 0 || totalDebitos.signum() < 0) {
            throw new IllegalArgumentException("Efeito do lançamento não pode ser negativo");
        }
        if (quantidade < 1) {
            throw new IllegalArgumentException("Efeito do lançamento deve contar ao menos um lançamento");
        }
    }

    public static EfeitoLancamento de(TipoLancamento tipo, Dinheiro valor) {
        return switch (tipo) {
            case CREDITO -> new EfeitoLancamento(valor.quantia(), ValoresMonetarios.ZERO, 1);
            case DEBITO -> new EfeitoLancamento(ValoresMonetarios.ZERO, valor.quantia(), 1);
        };
    }

    public static EfeitoLancamento entre(SaldoDiario antes, SaldoDiario depois) {
        return new EfeitoLancamento(
                depois.totalCreditos().subtract(antes.totalCreditos()),
                depois.totalDebitos().subtract(antes.totalDebitos()),
                depois.quantidadeLancamentos() - antes.quantidadeLancamentos());
    }
}

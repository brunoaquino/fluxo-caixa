package br.com.fluxocaixa.consolidado.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record MovimentoAcumulado(MovimentoDia movimento, BigDecimal saldoAcumulado) {

    public MovimentoAcumulado {
        Objects.requireNonNull(movimento, "movimento");
        saldoAcumulado = ValoresMonetarios.comDuasCasas(saldoAcumulado);
    }
}

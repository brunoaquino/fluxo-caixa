package br.com.fluxocaixa.consolidado.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record LinhaRelatorio(MovimentoDia movimento, BigDecimal saldoAcumulado) {

    public LinhaRelatorio {
        Objects.requireNonNull(movimento, "movimento");
        saldoAcumulado = ValoresMonetarios.comDuasCasas(saldoAcumulado);
    }
}

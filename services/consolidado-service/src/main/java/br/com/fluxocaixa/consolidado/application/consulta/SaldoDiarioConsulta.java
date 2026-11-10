package br.com.fluxocaixa.consolidado.application.consulta;

import br.com.fluxocaixa.consolidado.domain.model.LinhaRelatorio;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;
import br.com.fluxocaixa.consolidado.domain.model.ValoresMonetarios;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SaldoDiarioConsulta(
        LocalDate data,
        BigDecimal totalCreditos,
        BigDecimal totalDebitos,
        BigDecimal saldo,
        BigDecimal saldoAcumulado,
        int quantidadeLancamentos,
        Instant atualizadoEm) {

    public static SaldoDiarioConsulta de(MovimentoDia movimento, BigDecimal saldoAcumulado) {
        return new SaldoDiarioConsulta(
                movimento.data(),
                movimento.totalCreditos(),
                movimento.totalDebitos(),
                movimento.saldo(),
                ValoresMonetarios.comDuasCasas(saldoAcumulado),
                movimento.quantidadeLancamentos(),
                movimento.atualizadoEm());
    }

    public static SaldoDiarioConsulta de(LinhaRelatorio linha) {
        return de(linha.movimento(), linha.saldoAcumulado());
    }
}

package br.com.fluxocaixa.consolidado.adapters.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record SaldoDiarioResponse(
        LocalDate data,
        BigDecimal totalCreditos,
        BigDecimal totalDebitos,
        BigDecimal saldo,
        BigDecimal saldoAcumulado,
        int quantidadeLancamentos,
        Instant atualizadoEm) {
}

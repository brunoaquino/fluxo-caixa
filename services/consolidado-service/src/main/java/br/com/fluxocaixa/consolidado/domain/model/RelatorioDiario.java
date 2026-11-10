package br.com.fluxocaixa.consolidado.domain.model;

import br.com.fluxocaixa.consolidado.domain.exception.PeriodoConsultaInvalidoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RelatorioDiario {

    public static final int MAXIMO_DIAS = 366;

    private RelatorioDiario() {
    }

    public static void validar(LocalDate inicio, LocalDate fim) {
        if (inicio == null || fim == null) {
            throw new PeriodoConsultaInvalidoException(PeriodoConsultaInvalidoException.Motivo.DATA_OBRIGATORIA);
        }
        if (inicio.isAfter(fim)) {
            throw new PeriodoConsultaInvalidoException(PeriodoConsultaInvalidoException.Motivo.INICIO_APOS_FIM);
        }
        long dias = ChronoUnit.DAYS.between(inicio, fim) + 1;
        if (dias > MAXIMO_DIAS) {
            throw new PeriodoConsultaInvalidoException(PeriodoConsultaInvalidoException.Motivo.INTERVALO_MAIOR_QUE_366);
        }
    }

    public static List<LinhaRelatorio> montar(
            LocalDate inicio,
            LocalDate fim,
            BigDecimal acumuladoAntes,
            Map<LocalDate, MovimentoDia> movimentos) {
        validar(inicio, fim);
        BigDecimal acumulado = ValoresMonetarios.comDuasCasas(acumuladoAntes);
        List<LinhaRelatorio> linhas = new ArrayList<>();
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            MovimentoDia movimento = movimentos.getOrDefault(dia, MovimentoDia.vazio(dia));
            acumulado = ValoresMonetarios.comDuasCasas(acumulado.add(movimento.saldo()));
            linhas.add(new LinhaRelatorio(movimento, acumulado));
        }
        return List.copyOf(linhas);
    }
}

package br.com.fluxocaixa.consolidado.domain.model;

import br.com.fluxocaixa.consolidado.domain.exception.PeriodoConsultaInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelatorioDiarioTest {

    @Test
    void preencheDiasSemMovimentoEAcumulaSaldo() {
        LocalDate inicio = LocalDate.of(2026, 11, 10);
        LocalDate meio = inicio.plusDays(1);
        LocalDate fim = inicio.plusDays(2);
        MovimentoDia primeiro = new MovimentoDia(inicio, new BigDecimal("100.00"), new BigDecimal("40.00"), 2, Instant.parse("2026-11-10T12:00:00Z"));

        var linhas = RelatorioDiario.montar(inicio, fim, new BigDecimal("10"), Map.of(inicio, primeiro));

        assertThat(linhas).hasSize(3);
        assertThat(linhas.get(0).saldoAcumulado()).isEqualByComparingTo("70.00");
        assertThat(linhas.get(1).movimento().quantidadeLancamentos()).isZero();
        assertThat(linhas.get(1).movimento().saldo()).isEqualByComparingTo("0.00");
        assertThat(linhas.get(1).saldoAcumulado()).isEqualByComparingTo("70.00");
        assertThat(linhas.get(2).movimento().data()).isEqualTo(fim);
        assertThat(linhas.get(2).saldoAcumulado()).isEqualByComparingTo("70.00");
        assertThat(meio).isEqualTo(linhas.get(1).movimento().data());
    }

    @Test
    void aceita366DiasERejeitaPeriodoInvalido() {
        LocalDate inicio = LocalDate.of(2024, 1, 1);
        RelatorioDiario.validar(inicio, inicio.plusDays(365));
        assertThatThrownBy(() -> RelatorioDiario.validar(inicio, inicio.plusDays(366)))
                .isInstanceOf(PeriodoConsultaInvalidoException.class)
                .extracting(ex -> ((PeriodoConsultaInvalidoException) ex).motivo())
                .isEqualTo(PeriodoConsultaInvalidoException.Motivo.INTERVALO_MAIOR_QUE_366);
        assertThatThrownBy(() -> RelatorioDiario.validar(inicio.plusDays(1), inicio))
                .isInstanceOf(PeriodoConsultaInvalidoException.class)
                .extracting(ex -> ((PeriodoConsultaInvalidoException) ex).motivo())
                .isEqualTo(PeriodoConsultaInvalidoException.Motivo.INICIO_APOS_FIM);
        assertThatThrownBy(() -> RelatorioDiario.validar(null, inicio))
                .isInstanceOf(PeriodoConsultaInvalidoException.class)
                .extracting(ex -> ((PeriodoConsultaInvalidoException) ex).motivo())
                .isEqualTo(PeriodoConsultaInvalidoException.Motivo.DATA_OBRIGATORIA);
    }

    @Test
    void movimentoRejeitaQuantidadeNegativa() {
        assertThatThrownBy(() -> new MovimentoDia(LocalDate.now(), BigDecimal.ZERO, BigDecimal.ONE, -1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(MovimentoDia.vazio(LocalDate.of(2026, 1, 1)).saldo()).isEqualByComparingTo("0.00");
    }
}

package br.com.fluxocaixa.consolidado.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaldoDiarioTest {

    private static final UUID COMERCIANTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate DATA = LocalDate.of(2026, 11, 10);
    private static final Instant AGORA = Instant.parse("2026-11-10T18:00:00Z");

    @Test
    void aplicarEhComutativo() {
        LancamentoConsolidado credito = lancamento(TipoLancamento.CREDITO, "150.00");
        LancamentoConsolidado debito = lancamento(TipoLancamento.DEBITO, "40.00");

        SaldoDiario creditoPrimeiro = SaldoDiario.novo(COMERCIANTE, DATA).aplicar(credito, AGORA).aplicar(debito, AGORA);
        SaldoDiario debitoPrimeiro = SaldoDiario.novo(COMERCIANTE, DATA).aplicar(debito, AGORA).aplicar(credito, AGORA);

        assertThat(creditoPrimeiro.totalCreditos()).isEqualByComparingTo(debitoPrimeiro.totalCreditos());
        assertThat(creditoPrimeiro.totalDebitos()).isEqualByComparingTo(debitoPrimeiro.totalDebitos());
        assertThat(creditoPrimeiro.saldo()).isEqualByComparingTo("110.00");
        assertThat(creditoPrimeiro.saldo()).isEqualByComparingTo(debitoPrimeiro.saldo());
        assertThat(creditoPrimeiro.quantidadeLancamentos()).isEqualTo(2);
        assertThat(creditoPrimeiro.versao()).isEqualTo(2);
        assertThat(creditoPrimeiro.atualizadoEm()).isEqualTo(AGORA);
    }

    @Test
    void estornoEntraComoLancamentoDoTipoOposto() {
        LancamentoConsolidado venda = lancamento(TipoLancamento.CREDITO, "80.00");
        LancamentoConsolidado estorno = lancamento(TipoLancamento.DEBITO, "80.00");

        SaldoDiario saldo = SaldoDiario.novo(COMERCIANTE, DATA).aplicar(venda, AGORA).aplicar(estorno, AGORA);

        assertThat(saldo.saldo()).isEqualByComparingTo("0.00");
        assertThat(saldo.totalCreditos()).isEqualByComparingTo("80.00");
        assertThat(saldo.totalDebitos()).isEqualByComparingTo("80.00");
        assertThat(saldo.quantidadeLancamentos()).isEqualTo(2);
        assertThat(EfeitoLancamento.entre(SaldoDiario.novo(COMERCIANTE, DATA), SaldoDiario.novo(COMERCIANTE, DATA).aplicar(venda, AGORA))
                .totalCreditos()).isEqualByComparingTo("80.00");
    }

    @Test
    void rejeitaLancamentoDeOutroDiaOuComerciante() {
        SaldoDiario saldo = SaldoDiario.novo(COMERCIANTE, DATA);
        assertThatThrownBy(() -> saldo.aplicar(
                new LancamentoConsolidado(UUID.randomUUID(), DATA, TipoLancamento.CREDITO, Dinheiro.de("1.00")),
                AGORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> saldo.aplicar(
                new LancamentoConsolidado(COMERCIANTE, DATA.plusDays(1), TipoLancamento.DEBITO, Dinheiro.de("1.00")),
                AGORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> saldo.aplicar(null, AGORA)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void saldoNovoEhZero() {
        SaldoDiario saldo = SaldoDiario.novo(COMERCIANTE, DATA);
        assertThat(saldo.saldo()).isEqualByComparingTo("0.00");
        assertThat(saldo.quantidadeLancamentos()).isZero();
        assertThat(saldo.versao()).isZero();
        assertThat(saldo.atualizadoEm()).isNull();
        assertThat(saldo.comercianteId()).isEqualTo(COMERCIANTE);
        assertThat(saldo.data()).isEqualTo(DATA);
    }

    private static LancamentoConsolidado lancamento(TipoLancamento tipo, String valor) {
        return new LancamentoConsolidado(COMERCIANTE, DATA, tipo, Dinheiro.de(valor));
    }
}

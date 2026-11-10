package br.com.fluxocaixa.consolidado.application.servico;

import br.com.fluxocaixa.consolidado.application.comando.ComandoAplicarLancamento;
import br.com.fluxocaixa.consolidado.application.consulta.RelatorioPeriodoConsulta;
import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;
import br.com.fluxocaixa.consolidado.application.port.out.AplicacaoLancamentoPort;
import br.com.fluxocaixa.consolidado.application.port.out.CacheSaldoPort;
import br.com.fluxocaixa.consolidado.application.resultado.ResultadoAplicacao;
import br.com.fluxocaixa.consolidado.domain.model.Dinheiro;
import br.com.fluxocaixa.consolidado.domain.model.EfeitoLancamento;
import br.com.fluxocaixa.consolidado.domain.model.TipoLancamento;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AplicarLancamentoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-11-10T18:00:05Z");
    private static final UUID COMERCIANTE = UUID.randomUUID();

    @Test
    void aplicaEventoEInvalidaCache() {
        AtomicReference<EfeitoLancamento> efeito = new AtomicReference<>();
        AtomicBoolean invalidou = new AtomicBoolean();
        AplicarLancamentoService servico = servico((eventId, comercianteId, data, delta, processadoEm) -> {
            efeito.set(delta);
            return true;
        }, invalidou);

        ResultadoAplicacao resultado = servico.executar(comando(Instant.parse("2026-11-10T18:00:00Z")));

        assertThat(resultado).isInstanceOf(ResultadoAplicacao.Processado.class);
        assertThat(resultado.atraso()).isEqualTo(Duration.ofSeconds(5));
        assertThat(efeito.get().totalCreditos()).isEqualByComparingTo("150.00");
        assertThat(efeito.get().totalDebitos()).isEqualByComparingTo("0.00");
        assertThat(efeito.get().quantidade()).isEqualTo(1);
        assertThat(invalidou).isTrue();
    }

    @Test
    void eventoDuplicadoNaoInvalidaCache() {
        AtomicBoolean invalidou = new AtomicBoolean();
        AplicarLancamentoService servico = servico(
                (eventId, comercianteId, data, delta, processadoEm) -> false,
                invalidou);

        ResultadoAplicacao resultado = servico.executar(comando(AGORA.plusSeconds(30)));

        assertThat(resultado).isInstanceOf(ResultadoAplicacao.Duplicado.class);
        assertThat(resultado.atraso()).isZero();
        assertThat(invalidou).isFalse();
    }

    private static AplicarLancamentoService servico(AplicacaoLancamentoPort port, AtomicBoolean invalidou) {
        CacheSaldoPort cache = new CacheVazio(invalidou);
        return new AplicarLancamentoService(port, cache, Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    private static ComandoAplicarLancamento comando(Instant occurredAt) {
        return new ComandoAplicarLancamento(
                UUID.randomUUID(),
                UUID.randomUUID(),
                COMERCIANTE,
                TipoLancamento.CREDITO,
                Dinheiro.de("150.00"),
                LocalDate.of(2026, 11, 10),
                occurredAt,
                null);
    }

    private static final class CacheVazio implements CacheSaldoPort {

        private final AtomicBoolean invalidou;

        private CacheVazio(AtomicBoolean invalidou) {
            this.invalidou = invalidou;
        }

        @Override
        public Optional<SaldoDiarioConsulta> obterDia(UUID comercianteId, LocalDate data) {
            return Optional.empty();
        }

        @Override
        public void armazenarDia(UUID comercianteId, LocalDate data, SaldoDiarioConsulta consulta) {
        }

        @Override
        public Optional<RelatorioPeriodoConsulta> obterRelatorio(UUID comercianteId, LocalDate inicio, LocalDate fim) {
            return Optional.empty();
        }

        @Override
        public void armazenarRelatorio(
                UUID comercianteId,
                LocalDate inicio,
                LocalDate fim,
                RelatorioPeriodoConsulta relatorio) {
        }

        @Override
        public void invalidar(UUID comercianteId) {
            invalidou.set(true);
        }
    }
}

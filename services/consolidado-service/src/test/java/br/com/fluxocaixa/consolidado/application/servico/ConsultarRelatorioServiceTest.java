package br.com.fluxocaixa.consolidado.application.servico;

import br.com.fluxocaixa.consolidado.application.consulta.RelatorioPeriodoConsulta;
import br.com.fluxocaixa.consolidado.application.port.out.ConsultaSaldoPort;
import br.com.fluxocaixa.consolidado.domain.exception.PeriodoConsultaInvalidoException;
import br.com.fluxocaixa.consolidado.domain.model.LinhaRelatorio;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoAcumulado;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultarRelatorioServiceTest {

    private final UUID comerciante = UUID.randomUUID();
    private final ConsultarSaldoDiarioServiceTest.CacheMemoria cache = new ConsultarSaldoDiarioServiceTest.CacheMemoria();

    @Test
    void preencheLacunasEUsaCacheNaSegundaConsulta() {
        LocalDate inicio = LocalDate.of(2026, 11, 10);
        LocalDate fim = inicio.plusDays(1);
        AtomicInteger leituras = new AtomicInteger();
        ConsultaSaldoPort consulta = new ConsultaSaldoPort() {
            @Override
            public MovimentoAcumulado buscarDia(UUID comercianteId, LocalDate data) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<LinhaRelatorio> buscarPeriodo(UUID comercianteId, LocalDate de, LocalDate ate) {
                leituras.incrementAndGet();
                return List.of(
                        new LinhaRelatorio(
                                new MovimentoDia(inicio, new BigDecimal("20.00"), new BigDecimal("5.00"), 1, null),
                                new BigDecimal("20.00")),
                        new LinhaRelatorio(MovimentoDia.vazio(fim), new BigDecimal("20.00")));
            }
        };
        ConsultarRelatorioService servico = new ConsultarRelatorioService(consulta, cache);

        RelatorioPeriodoConsulta primeiro = servico.consultar(comerciante, inicio, fim);
        RelatorioPeriodoConsulta segundo = servico.consultar(comerciante, inicio, fim);

        assertThat(primeiro.dias()).hasSize(2);
        assertThat(primeiro.dias().getFirst().saldo()).isEqualByComparingTo("15.00");
        assertThat(primeiro.dias().getFirst().saldoAcumulado()).isEqualByComparingTo("20.00");
        assertThat(primeiro.dias().get(1).quantidadeLancamentos()).isZero();
        assertThat(primeiro.dias().get(1).saldoAcumulado()).isEqualByComparingTo("20.00");
        assertThat(segundo).isEqualTo(primeiro);
        assertThat(leituras).hasValue(1);
    }

    @Test
    void periodoInvalidoNaoConsultaCacheNemBanco() {
        ConsultarRelatorioService servico = new ConsultarRelatorioService(new ConsultaSaldoPort() {
            @Override
            public MovimentoAcumulado buscarDia(UUID comercianteId, LocalDate data) {
                throw new AssertionError("banco");
            }

            @Override
            public List<LinhaRelatorio> buscarPeriodo(UUID comercianteId, LocalDate inicio, LocalDate fim) {
                throw new AssertionError("banco");
            }
        }, cache);

        LocalDate inicio = LocalDate.of(2026, 11, 10);
        assertThatThrownBy(() -> servico.consultar(comerciante, inicio.plusDays(1), inicio))
                .isInstanceOf(PeriodoConsultaInvalidoException.class);
        assertThat(cache.obterRelatorio(comerciante, inicio.plusDays(1), inicio)).isEmpty();
    }
}

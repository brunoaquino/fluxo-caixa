package br.com.fluxocaixa.consolidado.application.servico;

import br.com.fluxocaixa.consolidado.application.consulta.RelatorioPeriodoConsulta;
import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;
import br.com.fluxocaixa.consolidado.application.port.out.CacheSaldoPort;
import br.com.fluxocaixa.consolidado.application.port.out.ConsultaSaldoPort;
import br.com.fluxocaixa.consolidado.domain.model.LinhaRelatorio;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoAcumulado;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultarSaldoDiarioServiceTest {

    private final UUID comerciante = UUID.randomUUID();
    private final LocalDate data = LocalDate.of(2026, 11, 10);
    private final CacheMemoria cache = new CacheMemoria();

    @Test
    void diaSemMovimentoRetornaZerosComSaldoAcumuladoAnterior() {
        ConsultaSaldoPort consulta = new ConsultaFixa(
                new MovimentoAcumulado(MovimentoDia.vazio(data), new BigDecimal("80.00")));
        ConsultarSaldoDiarioService servico = new ConsultarSaldoDiarioService(consulta, cache);

        SaldoDiarioConsulta saldo = servico.consultar(comerciante, data);

        assertThat(saldo.totalCreditos()).isEqualByComparingTo("0.00");
        assertThat(saldo.totalDebitos()).isEqualByComparingTo("0.00");
        assertThat(saldo.saldo()).isEqualByComparingTo("0.00");
        assertThat(saldo.saldoAcumulado()).isEqualByComparingTo("80.00");
        assertThat(saldo.quantidadeLancamentos()).isZero();
        assertThat(saldo.atualizadoEm()).isNull();
        assertThat(cache.dias).containsKey(comerciante + ":" + data);
    }

    @Test
    void cacheHitNaoConsultaOBanco() {
        SaldoDiarioConsulta armazenado = SaldoDiarioConsulta.de(
                MovimentoDia.vazio(data),
                new BigDecimal("10.00"));
        cache.dias.put(comerciante + ":" + data, armazenado);
        ConsultarSaldoDiarioService servico = new ConsultarSaldoDiarioService(new ConsultaProibida(), cache);

        assertThat(servico.consultar(comerciante, data)).isEqualTo(armazenado);
    }

    @Test
    void carregaMovimentoDoBancoQuandoNaoHaCache() {
        MovimentoDia movimento = new MovimentoDia(
                data,
                new BigDecimal("150.00"),
                new BigDecimal("20.00"),
                2,
                Instant.parse("2026-11-10T15:00:00Z"));
        ConsultarSaldoDiarioService servico = new ConsultarSaldoDiarioService(
                new ConsultaFixa(new MovimentoAcumulado(movimento, new BigDecimal("130.00"))),
                cache);

        SaldoDiarioConsulta saldo = servico.consultar(comerciante, data);

        assertThat(saldo.saldo()).isEqualByComparingTo("130.00");
        assertThat(saldo.saldoAcumulado()).isEqualByComparingTo("130.00");
        assertThat(saldo.quantidadeLancamentos()).isEqualTo(2);
        assertThat(saldo.atualizadoEm()).isEqualTo(Instant.parse("2026-11-10T15:00:00Z"));
    }

    private static final class ConsultaProibida implements ConsultaSaldoPort {

        @Override
        public MovimentoAcumulado buscarDia(UUID comercianteId, LocalDate data) {
            throw new AssertionError("não deveria consultar o banco");
        }

        @Override
        public List<LinhaRelatorio> buscarPeriodo(UUID comercianteId, LocalDate inicio, LocalDate fim) {
            throw new AssertionError("não deveria consultar o banco");
        }
    }

    private record ConsultaFixa(MovimentoAcumulado dia) implements ConsultaSaldoPort {

        @Override
        public MovimentoAcumulado buscarDia(UUID comercianteId, LocalDate data) {
            return dia;
        }

        @Override
        public List<LinhaRelatorio> buscarPeriodo(UUID comercianteId, LocalDate inicio, LocalDate fim) {
            throw new UnsupportedOperationException();
        }
    }

    static final class CacheMemoria implements CacheSaldoPort {

        private final Map<String, SaldoDiarioConsulta> dias = new HashMap<>();
        private final Map<String, RelatorioPeriodoConsulta> relatorios = new HashMap<>();

        @Override
        public Optional<SaldoDiarioConsulta> obterDia(UUID comercianteId, LocalDate data) {
            return Optional.ofNullable(dias.get(comercianteId + ":" + data));
        }

        @Override
        public void armazenarDia(UUID comercianteId, LocalDate data, SaldoDiarioConsulta consulta) {
            dias.put(comercianteId + ":" + data, consulta);
        }

        @Override
        public Optional<RelatorioPeriodoConsulta> obterRelatorio(UUID comercianteId, LocalDate inicio, LocalDate fim) {
            return Optional.ofNullable(relatorios.get(comercianteId + ":" + inicio + ":" + fim));
        }

        @Override
        public void armazenarRelatorio(
                UUID comercianteId,
                LocalDate inicio,
                LocalDate fim,
                RelatorioPeriodoConsulta relatorio) {
            relatorios.put(comercianteId + ":" + inicio + ":" + fim, relatorio);
        }

        @Override
        public void invalidar(UUID comercianteId) {
            dias.clear();
            relatorios.clear();
        }
    }
}

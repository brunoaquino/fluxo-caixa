package br.com.fluxocaixa.consolidado.application.servico;

import br.com.fluxocaixa.consolidado.application.consulta.RelatorioPeriodoConsulta;
import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;
import br.com.fluxocaixa.consolidado.application.port.in.ConsultarRelatorioUseCase;
import br.com.fluxocaixa.consolidado.application.port.out.CacheSaldoPort;
import br.com.fluxocaixa.consolidado.application.port.out.ConsultaSaldoPort;
import br.com.fluxocaixa.consolidado.domain.model.RelatorioDiario;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ConsultarRelatorioService implements ConsultarRelatorioUseCase {

    private final ConsultaSaldoPort consulta;
    private final CacheSaldoPort cache;

    public ConsultarRelatorioService(ConsultaSaldoPort consulta, CacheSaldoPort cache) {
        this.consulta = Objects.requireNonNull(consulta, "consulta");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public RelatorioPeriodoConsulta consultar(UUID comercianteId, LocalDate inicio, LocalDate fim) {
        Objects.requireNonNull(comercianteId, "comercianteId");
        RelatorioDiario.validar(inicio, fim);
        return cache.obterRelatorio(comercianteId, inicio, fim).orElseGet(() -> {
            RelatorioPeriodoConsulta relatorio = carregar(comercianteId, inicio, fim);
            cache.armazenarRelatorio(comercianteId, inicio, fim, relatorio);
            return relatorio;
        });
    }

    private RelatorioPeriodoConsulta carregar(UUID comercianteId, LocalDate inicio, LocalDate fim) {
        List<SaldoDiarioConsulta> dias = consulta.buscarPeriodo(comercianteId, inicio, fim).stream()
                .map(SaldoDiarioConsulta::de)
                .toList();
        return new RelatorioPeriodoConsulta(inicio, fim, dias);
    }
}

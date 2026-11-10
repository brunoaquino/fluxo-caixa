package br.com.fluxocaixa.consolidado.application.port.out;

import br.com.fluxocaixa.consolidado.application.consulta.RelatorioPeriodoConsulta;
import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface CacheSaldoPort {

    Optional<SaldoDiarioConsulta> obterDia(UUID comercianteId, LocalDate data);

    void armazenarDia(UUID comercianteId, LocalDate data, SaldoDiarioConsulta consulta);

    Optional<RelatorioPeriodoConsulta> obterRelatorio(UUID comercianteId, LocalDate inicio, LocalDate fim);

    void armazenarRelatorio(UUID comercianteId, LocalDate inicio, LocalDate fim, RelatorioPeriodoConsulta relatorio);

    void invalidar(UUID comercianteId);
}

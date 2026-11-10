package br.com.fluxocaixa.consolidado.application.port.in;

import br.com.fluxocaixa.consolidado.application.consulta.RelatorioPeriodoConsulta;

import java.time.LocalDate;
import java.util.UUID;

public interface ConsultarRelatorioUseCase {

    RelatorioPeriodoConsulta consultar(UUID comercianteId, LocalDate inicio, LocalDate fim);
}

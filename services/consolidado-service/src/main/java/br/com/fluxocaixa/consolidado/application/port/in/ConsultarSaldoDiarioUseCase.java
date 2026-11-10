package br.com.fluxocaixa.consolidado.application.port.in;

import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;

import java.time.LocalDate;
import java.util.UUID;

public interface ConsultarSaldoDiarioUseCase {

    SaldoDiarioConsulta consultar(UUID comercianteId, LocalDate data);
}

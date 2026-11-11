package br.com.fluxocaixa.consolidado.application.port.out;

import br.com.fluxocaixa.consolidado.domain.model.LinhaRelatorio;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoAcumulado;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ConsultaSaldoPort {

    MovimentoAcumulado buscarDia(UUID comercianteId, LocalDate data);

    List<LinhaRelatorio> buscarPeriodo(UUID comercianteId, LocalDate inicio, LocalDate fim);
}

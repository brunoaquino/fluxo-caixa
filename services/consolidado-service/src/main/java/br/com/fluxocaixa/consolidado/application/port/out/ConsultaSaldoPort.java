package br.com.fluxocaixa.consolidado.application.port.out;

import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface ConsultaSaldoPort {

    Optional<MovimentoDia> buscarDia(UUID comercianteId, LocalDate data);

    BigDecimal saldoAcumuladoAte(UUID comercianteId, LocalDate data);

    BigDecimal saldoAcumuladoAntesDe(UUID comercianteId, LocalDate data);

    Map<LocalDate, MovimentoDia> buscarNoPeriodo(UUID comercianteId, LocalDate inicio, LocalDate fim);
}

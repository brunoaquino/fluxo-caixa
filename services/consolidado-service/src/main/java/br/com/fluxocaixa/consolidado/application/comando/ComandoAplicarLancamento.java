package br.com.fluxocaixa.consolidado.application.comando;

import br.com.fluxocaixa.consolidado.domain.model.Dinheiro;
import br.com.fluxocaixa.consolidado.domain.model.TipoLancamento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record ComandoAplicarLancamento(
        UUID eventId,
        UUID lancamentoId,
        UUID comercianteId,
        TipoLancamento tipo,
        Dinheiro valor,
        LocalDate dataLancamento,
        Instant occurredAt,
        UUID lancamentoEstornadoId) {

    public ComandoAplicarLancamento {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(lancamentoId, "lancamentoId");
        Objects.requireNonNull(comercianteId, "comercianteId");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(valor, "valor");
        Objects.requireNonNull(dataLancamento, "dataLancamento");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

package br.com.fluxocaixa.consolidado.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record LancamentoConsolidado(
        UUID comercianteId,
        LocalDate data,
        TipoLancamento tipo,
        Dinheiro valor) {

    public LancamentoConsolidado {
        Objects.requireNonNull(comercianteId, "comercianteId");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(valor, "valor");
    }
}

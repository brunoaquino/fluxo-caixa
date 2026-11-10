package br.com.fluxocaixa.consolidado.application.port.out;

import br.com.fluxocaixa.consolidado.domain.model.EfeitoLancamento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public interface AplicacaoLancamentoPort {

    /**
     * Grava o evento e o incremento do saldo na mesma transação.
     *
     * @return {@code false} quando o eventId já foi processado
     */
    boolean aplicarSeNovo(
            UUID eventId,
            UUID comercianteId,
            LocalDate data,
            EfeitoLancamento efeito,
            Instant processadoEm);
}

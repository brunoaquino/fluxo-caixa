package br.com.fluxocaixa.lancamentos.domain.lancamento;

import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento de domínio publicado para o consolidado. Estorno usa o mesmo tipo,
 * com {@code lancamentoEstornadoId} preenchido e {@code tipo} já invertido.
 */
public record LancamentoRegistrado(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID lancamentoId,
        UUID comercianteId,
        TipoLancamento tipo,
        Dinheiro valor,
        LocalDate dataLancamento,
        UUID lancamentoEstornadoId
) {

    public static final String TIPO = "LancamentoRegistrado";
    public static final int VERSAO = 1;

    public LancamentoRegistrado {
        if (eventId == null || occurredAt == null || lancamentoId == null || comercianteId == null
                || tipo == null || valor == null || dataLancamento == null) {
            throw new IllegalArgumentException("Evento de lançamento incompleto");
        }
        if (!TIPO.equals(eventType) || eventVersion != VERSAO) {
            throw new IllegalArgumentException("Versão de evento não suportada");
        }
    }
}

package br.com.fluxocaixa.lancamentos.adapters.in.web.dto;

import br.com.fluxocaixa.lancamentos.domain.lancamento.TipoLancamento;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record LancamentoResponse(
        UUID id,
        TipoLancamento tipo,
        String valor,
        String moeda,
        LocalDate dataLancamento,
        String descricao,
        String categoria,
        UUID lancamentoEstornadoId,
        Instant criadoEm
) {
}

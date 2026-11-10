package br.com.fluxocaixa.lancamentos.application.comando;

import br.com.fluxocaixa.lancamentos.domain.lancamento.TipoLancamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RegistrarLancamentoComando(
        UUID comercianteId,
        UUID chaveIdempotencia,
        TipoLancamento tipo,
        BigDecimal valor,
        LocalDate dataLancamento,
        String descricao,
        String categoria
) {
}

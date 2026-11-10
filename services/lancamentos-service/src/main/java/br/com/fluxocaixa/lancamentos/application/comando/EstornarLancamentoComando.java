package br.com.fluxocaixa.lancamentos.application.comando;

import java.util.UUID;

public record EstornarLancamentoComando(
        UUID comercianteId,
        UUID lancamentoId,
        UUID chaveIdempotencia,
        String motivo
) {
}

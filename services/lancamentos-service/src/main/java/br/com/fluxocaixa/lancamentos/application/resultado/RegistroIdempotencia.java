package br.com.fluxocaixa.lancamentos.application.resultado;

import java.util.UUID;

public record RegistroIdempotencia(
        UUID chave,
        UUID comercianteId,
        UUID lancamentoId,
        String impressaoDigital
) {
}

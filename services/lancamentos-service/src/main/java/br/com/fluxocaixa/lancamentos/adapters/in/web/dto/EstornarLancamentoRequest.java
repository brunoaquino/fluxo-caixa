package br.com.fluxocaixa.lancamentos.adapters.in.web.dto;

import jakarta.validation.constraints.Size;

public record EstornarLancamentoRequest(
        @Size(max = 255, message = "deve ter no máximo 255 caracteres")
        String motivo
) {
}

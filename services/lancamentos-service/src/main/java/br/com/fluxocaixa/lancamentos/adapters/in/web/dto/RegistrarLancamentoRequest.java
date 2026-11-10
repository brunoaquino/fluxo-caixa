package br.com.fluxocaixa.lancamentos.adapters.in.web.dto;

import br.com.fluxocaixa.lancamentos.domain.lancamento.TipoLancamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarLancamentoRequest(
        @NotNull(message = "é obrigatório")
        TipoLancamento tipo,
        @NotNull(message = "é obrigatório")
        @DecimalMin(value = "0.01", message = "deve ser maior que zero")
        @Digits(integer = 17, fraction = 2, message = "deve ter no máximo 2 casas decimais")
        BigDecimal valor,
        @NotNull(message = "é obrigatória")
        LocalDate dataLancamento,
        @NotBlank(message = "é obrigatória")
        @Size(min = 1, max = 255, message = "deve ter entre 1 e 255 caracteres")
        String descricao,
        @Size(max = 60, message = "deve ter no máximo 60 caracteres")
        String categoria
) {
}

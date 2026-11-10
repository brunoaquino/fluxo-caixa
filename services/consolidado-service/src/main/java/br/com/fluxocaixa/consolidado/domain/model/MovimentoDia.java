package br.com.fluxocaixa.consolidado.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record MovimentoDia(
        LocalDate data,
        BigDecimal totalCreditos,
        BigDecimal totalDebitos,
        int quantidadeLancamentos,
        Instant atualizadoEm) {

    public MovimentoDia {
        Objects.requireNonNull(data, "data");
        totalCreditos = ValoresMonetarios.comDuasCasas(totalCreditos);
        totalDebitos = ValoresMonetarios.comDuasCasas(totalDebitos);
        if (quantidadeLancamentos < 0) {
            throw new IllegalArgumentException("Quantidade de lançamentos não pode ser negativa");
        }
    }

    public static MovimentoDia vazio(LocalDate data) {
        return new MovimentoDia(data, ValoresMonetarios.ZERO, ValoresMonetarios.ZERO, 0, null);
    }

    public BigDecimal saldo() {
        return totalCreditos.subtract(totalDebitos);
    }
}

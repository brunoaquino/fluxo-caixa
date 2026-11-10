package br.com.fluxocaixa.consolidado.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado do saldo de um comerciante em um dia de competência.
 * A aplicação é comutativa: só soma créditos ou débitos, então a ordem dos eventos não importa.
 */
public final class SaldoDiario {

    private final UUID comercianteId;
    private final LocalDate data;
    private final BigDecimal totalCreditos;
    private final BigDecimal totalDebitos;
    private final int quantidadeLancamentos;
    private final long versao;
    private final Instant atualizadoEm;

    private SaldoDiario(
            UUID comercianteId,
            LocalDate data,
            BigDecimal totalCreditos,
            BigDecimal totalDebitos,
            int quantidadeLancamentos,
            long versao,
            Instant atualizadoEm) {
        this.comercianteId = Objects.requireNonNull(comercianteId, "comercianteId");
        this.data = Objects.requireNonNull(data, "data");
        this.totalCreditos = ValoresMonetarios.exato(totalCreditos);
        this.totalDebitos = ValoresMonetarios.exato(totalDebitos);
        if (quantidadeLancamentos < 0) {
            throw new IllegalArgumentException("Quantidade de lançamentos não pode ser negativa");
        }
        if (versao < 0) {
            throw new IllegalArgumentException("Versão não pode ser negativa");
        }
        this.quantidadeLancamentos = quantidadeLancamentos;
        this.versao = versao;
        this.atualizadoEm = atualizadoEm;
    }

    public static SaldoDiario novo(UUID comercianteId, LocalDate data) {
        return new SaldoDiario(
                comercianteId,
                data,
                ValoresMonetarios.ZERO,
                ValoresMonetarios.ZERO,
                0,
                0,
                null);
    }

    public SaldoDiario aplicar(LancamentoConsolidado lancamento, Instant quando) {
        Objects.requireNonNull(lancamento, "lancamento");
        Objects.requireNonNull(quando, "quando");
        if (!comercianteId.equals(lancamento.comercianteId()) || !data.equals(lancamento.data())) {
            throw new IllegalArgumentException("Lançamento não pertence a este saldo diário");
        }
        EfeitoLancamento efeito = EfeitoLancamento.de(lancamento.tipo(), lancamento.valor());
        return new SaldoDiario(
                comercianteId,
                data,
                totalCreditos.add(efeito.totalCreditos()),
                totalDebitos.add(efeito.totalDebitos()),
                quantidadeLancamentos + efeito.quantidade(),
                versao + 1,
                quando);
    }

    public BigDecimal saldo() {
        return totalCreditos.subtract(totalDebitos);
    }

    public UUID comercianteId() {
        return comercianteId;
    }

    public LocalDate data() {
        return data;
    }

    public BigDecimal totalCreditos() {
        return totalCreditos;
    }

    public BigDecimal totalDebitos() {
        return totalDebitos;
    }

    public int quantidadeLancamentos() {
        return quantidadeLancamentos;
    }

    public long versao() {
        return versao;
    }

    public Instant atualizadoEm() {
        return atualizadoEm;
    }
}

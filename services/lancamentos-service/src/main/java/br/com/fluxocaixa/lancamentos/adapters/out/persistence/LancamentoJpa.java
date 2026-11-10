package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import br.com.fluxocaixa.lancamentos.domain.lancamento.TipoLancamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lancamentos")
class LancamentoJpa {

    @Id
    private UUID id;

    @Column(name = "comerciante_id", nullable = false)
    private UUID comercianteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoLancamento tipo;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false, length = 3)
    private String moeda;

    @Column(name = "data_lancamento", nullable = false)
    private LocalDate dataLancamento;

    @Column(nullable = false, length = 255)
    private String descricao;

    @Column(length = 60)
    private String categoria;

    @Column(name = "lancamento_estornado_id")
    private UUID lancamentoEstornadoId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected LancamentoJpa() {
    }

    LancamentoJpa(
            UUID id,
            UUID comercianteId,
            TipoLancamento tipo,
            BigDecimal valor,
            String moeda,
            LocalDate dataLancamento,
            String descricao,
            String categoria,
            UUID lancamentoEstornadoId,
            Instant criadoEm
    ) {
        this.id = id;
        this.comercianteId = comercianteId;
        this.tipo = tipo;
        this.valor = valor;
        this.moeda = moeda;
        this.dataLancamento = dataLancamento;
        this.descricao = descricao;
        this.categoria = categoria;
        this.lancamentoEstornadoId = lancamentoEstornadoId;
        this.criadoEm = criadoEm;
    }

    UUID getId() {
        return id;
    }

    UUID getComercianteId() {
        return comercianteId;
    }

    TipoLancamento getTipo() {
        return tipo;
    }

    BigDecimal getValor() {
        return valor;
    }

    String getMoeda() {
        return moeda;
    }

    LocalDate getDataLancamento() {
        return dataLancamento;
    }

    String getDescricao() {
        return descricao;
    }

    String getCategoria() {
        return categoria;
    }

    UUID getLancamentoEstornadoId() {
        return lancamentoEstornadoId;
    }

    Instant getCriadoEm() {
        return criadoEm;
    }
}

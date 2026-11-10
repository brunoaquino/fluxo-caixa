package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotencia")
class IdempotenciaJpa {

    @EmbeddedId
    private IdempotenciaId id;

    @Column(name = "lancamento_id", nullable = false)
    private UUID lancamentoId;

    @Column(name = "impressao_digital", nullable = false, length = 64)
    private String impressaoDigital;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    protected IdempotenciaJpa() {
    }

    IdempotenciaJpa(IdempotenciaId id, UUID lancamentoId, String impressaoDigital, Instant criadoEm) {
        this.id = id;
        this.lancamentoId = lancamentoId;
        this.impressaoDigital = impressaoDigital;
        this.criadoEm = criadoEm;
    }

    IdempotenciaId getId() {
        return id;
    }

    UUID getLancamentoId() {
        return lancamentoId;
    }

    String getImpressaoDigital() {
        return impressaoDigital;
    }
}

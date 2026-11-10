package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class IdempotenciaId implements Serializable {

    @Column(name = "chave", nullable = false)
    private UUID chave;

    @Column(name = "comerciante_id", nullable = false)
    private UUID comercianteId;

    protected IdempotenciaId() {
    }

    IdempotenciaId(UUID chave, UUID comercianteId) {
        this.chave = chave;
        this.comercianteId = comercianteId;
    }

    UUID getChave() {
        return chave;
    }

    UUID getComercianteId() {
        return comercianteId;
    }

    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof IdempotenciaId id)) {
            return false;
        }
        return Objects.equals(chave, id.chave) && Objects.equals(comercianteId, id.comercianteId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chave, comercianteId);
    }
}

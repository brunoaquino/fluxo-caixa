package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface IdempotenciaSpringData extends JpaRepository<IdempotenciaJpa, IdempotenciaId> {
}

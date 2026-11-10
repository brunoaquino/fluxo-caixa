package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface OutboxEventoSpringData extends JpaRepository<OutboxEventoJpa, UUID> {
}

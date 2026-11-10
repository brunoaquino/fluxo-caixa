package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

interface LancamentoSpringData extends JpaRepository<LancamentoJpa, UUID> {

    Optional<LancamentoJpa> findByIdAndComercianteId(UUID id, UUID comercianteId);

    boolean existsByComercianteIdAndLancamentoEstornadoId(UUID comercianteId, UUID lancamentoEstornadoId);

    Page<LancamentoJpa> findByComercianteIdAndDataLancamentoOrderByCriadoEmAsc(
            UUID comercianteId,
            LocalDate dataLancamento,
            Pageable pageable
    );
}

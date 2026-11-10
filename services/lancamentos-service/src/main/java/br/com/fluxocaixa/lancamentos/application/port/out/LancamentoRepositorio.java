package br.com.fluxocaixa.lancamentos.application.port.out;

import br.com.fluxocaixa.lancamentos.application.resultado.Pagina;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LancamentoRepositorio {

    void salvar(Lancamento lancamento);

    Optional<Lancamento> buscarPorId(UUID id, UUID comercianteId);

    boolean existeEstornoPara(UUID comercianteId, UUID lancamentoOriginalId);

    Pagina<Lancamento> listarPorData(UUID comercianteId, LocalDate data, int pagina, int tamanho);
}

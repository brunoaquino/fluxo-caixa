package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepositorio;
import br.com.fluxocaixa.lancamentos.application.resultado.Pagina;
import br.com.fluxocaixa.lancamentos.domain.excecao.LancamentoJaEstornadoException;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LancamentoRepositorioJpa implements LancamentoRepositorio {

    private final LancamentoSpringData springData;

    public LancamentoRepositorioJpa(LancamentoSpringData springData) {
        this.springData = springData;
    }

    @Override
    public void salvar(Lancamento lancamento) {
        try {
            springData.saveAndFlush(MapeadorLancamento.paraJpa(lancamento));
        } catch (DataIntegrityViolationException ex) {
            if (causaContem(ex, "uq_lancamentos_estorno")) {
                throw new LancamentoJaEstornadoException(lancamento.lancamentoEstornadoId());
            }
            throw ex;
        }
    }

    @Override
    public Optional<Lancamento> buscarPorId(UUID id, UUID comercianteId) {
        return springData.findByIdAndComercianteId(id, comercianteId).map(MapeadorLancamento::paraDominio);
    }

    @Override
    public boolean existeEstornoPara(UUID comercianteId, UUID lancamentoOriginalId) {
        return springData.existsByComercianteIdAndLancamentoEstornadoId(comercianteId, lancamentoOriginalId);
    }

    @Override
    public Pagina<Lancamento> listarPorData(UUID comercianteId, LocalDate data, int pagina, int tamanho) {
        var resultado = springData.findByComercianteIdAndDataLancamentoOrderByCriadoEmAsc(
                comercianteId,
                data,
                PageRequest.of(pagina, tamanho)
        );
        var conteudo = resultado.getContent().stream().map(MapeadorLancamento::paraDominio).toList();
        return new Pagina<>(
                conteudo,
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages()
        );
    }

    private static boolean causaContem(Throwable ex, String trecho) {
        Throwable atual = ex;
        while (atual != null) {
            if (atual.getMessage() != null && atual.getMessage().contains(trecho)) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }
}

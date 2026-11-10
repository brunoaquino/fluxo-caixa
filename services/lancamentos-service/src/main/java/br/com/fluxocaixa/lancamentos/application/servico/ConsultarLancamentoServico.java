package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.excecao.LancamentoNaoEncontradoException;
import br.com.fluxocaixa.lancamentos.application.port.in.ConsultarLancamento;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepositorio;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ConsultarLancamentoServico implements ConsultarLancamento {

    private final LancamentoRepositorio lancamentos;

    public ConsultarLancamentoServico(LancamentoRepositorio lancamentos) {
        this.lancamentos = lancamentos;
    }

    @Override
    @Transactional(readOnly = true)
    public Lancamento executar(UUID comercianteId, UUID lancamentoId) {
        return lancamentos.buscarPorId(lancamentoId, comercianteId)
                .orElseThrow(() -> new LancamentoNaoEncontradoException(lancamentoId));
    }
}

package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.port.in.ListarLancamentos;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepositorio;
import br.com.fluxocaixa.lancamentos.application.resultado.Pagina;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class ListarLancamentosServico implements ListarLancamentos {

    private final LancamentoRepositorio lancamentos;

    public ListarLancamentosServico(LancamentoRepositorio lancamentos) {
        this.lancamentos = lancamentos;
    }

    @Override
    @Transactional(readOnly = true)
    public Pagina<Lancamento> executar(UUID comercianteId, LocalDate data, int pagina, int tamanho) {
        return lancamentos.listarPorData(comercianteId, data, pagina, tamanho);
    }
}

package br.com.fluxocaixa.lancamentos.application.port.in;

import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;

import java.util.UUID;

public interface ConsultarLancamento {

    Lancamento executar(UUID comercianteId, UUID lancamentoId);
}

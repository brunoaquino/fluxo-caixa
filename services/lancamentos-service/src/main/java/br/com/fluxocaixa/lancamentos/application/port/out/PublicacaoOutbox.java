package br.com.fluxocaixa.lancamentos.application.port.out;

import br.com.fluxocaixa.lancamentos.domain.lancamento.LancamentoRegistrado;

public interface PublicacaoOutbox {

    void registrar(LancamentoRegistrado evento);
}

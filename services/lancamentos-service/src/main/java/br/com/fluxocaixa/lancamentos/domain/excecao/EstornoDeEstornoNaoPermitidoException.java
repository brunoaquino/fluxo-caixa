package br.com.fluxocaixa.lancamentos.domain.excecao;

import java.util.UUID;

public final class EstornoDeEstornoNaoPermitidoException extends ExcecaoDeDominio {

    public EstornoDeEstornoNaoPermitidoException(UUID lancamentoId) {
        super("Lançamento " + lancamentoId + " é um estorno e não pode ser estornado");
    }
}

package br.com.fluxocaixa.lancamentos.domain.excecao;

import java.util.UUID;

public final class LancamentoJaEstornadoException extends ExcecaoDeDominio {

    public LancamentoJaEstornadoException(UUID lancamentoId) {
        super("Lançamento " + lancamentoId + " já foi estornado");
    }
}

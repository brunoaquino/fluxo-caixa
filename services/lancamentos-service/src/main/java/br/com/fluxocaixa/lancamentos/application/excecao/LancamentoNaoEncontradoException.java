package br.com.fluxocaixa.lancamentos.application.excecao;

import java.util.UUID;

public class LancamentoNaoEncontradoException extends RuntimeException {

    public LancamentoNaoEncontradoException(UUID lancamentoId) {
        super("Lançamento " + lancamentoId + " não encontrado");
    }
}

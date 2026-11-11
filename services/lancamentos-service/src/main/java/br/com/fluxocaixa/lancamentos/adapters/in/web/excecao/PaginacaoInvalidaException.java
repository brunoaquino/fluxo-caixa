package br.com.fluxocaixa.lancamentos.adapters.in.web.excecao;

public class PaginacaoInvalidaException extends RuntimeException {

    public PaginacaoInvalidaException() {
        super("page deve estar entre 0 e 1000 e size entre 1 e 100");
    }
}

package br.com.fluxocaixa.lancamentos.adapters.in.web.excecao;

public class PaginacaoInvalidaException extends RuntimeException {

    public PaginacaoInvalidaException() {
        super("page deve ser >= 0 e size deve estar entre 1 e 100");
    }
}

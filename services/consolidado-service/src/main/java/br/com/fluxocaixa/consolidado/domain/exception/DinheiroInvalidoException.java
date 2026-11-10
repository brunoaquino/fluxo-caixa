package br.com.fluxocaixa.consolidado.domain.exception;

public class DinheiroInvalidoException extends RuntimeException {

    public DinheiroInvalidoException(String mensagem) {
        super(mensagem);
    }
}

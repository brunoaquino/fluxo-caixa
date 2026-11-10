package br.com.fluxocaixa.consolidado.adapters.in.messaging;

public class MensagemInvalidaException extends RuntimeException {

    public MensagemInvalidaException(String mensagem) {
        super(mensagem);
    }

    public MensagemInvalidaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

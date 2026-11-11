package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

public class TimeoutDePublicacaoException extends RuntimeException {

    public TimeoutDePublicacaoException(Throwable causa) {
        super("Tempo esgotado ao publicar no SNS", causa);
    }
}

package br.com.fluxocaixa.lancamentos.adapters.in.web.excecao;

public class ChaveIdempotenciaInvalidaException extends RuntimeException {

    public ChaveIdempotenciaInvalidaException() {
        super("Idempotency-Key deve ser um UUID");
    }
}

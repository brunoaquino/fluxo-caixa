package br.com.fluxocaixa.lancamentos.adapters.in.web.excecao;

public class ChaveIdempotenciaInvalidaException extends RuntimeException {

    public ChaveIdempotenciaInvalidaException(String valor) {
        super("Idempotency-Key deve ser um UUID: " + valor);
    }
}

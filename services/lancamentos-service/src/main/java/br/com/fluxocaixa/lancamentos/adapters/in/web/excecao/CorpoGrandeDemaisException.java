package br.com.fluxocaixa.lancamentos.adapters.in.web.excecao;

public class CorpoGrandeDemaisException extends RuntimeException {

    public CorpoGrandeDemaisException() {
        super("O corpo da requisição excede o limite de 8 KB");
    }
}

package br.com.fluxocaixa.consolidado.adapters.in.web;

public class LimiteConsultasExcedidoException extends RuntimeException {

    public LimiteConsultasExcedidoException() {
        super("Limite de consultas excedido");
    }
}

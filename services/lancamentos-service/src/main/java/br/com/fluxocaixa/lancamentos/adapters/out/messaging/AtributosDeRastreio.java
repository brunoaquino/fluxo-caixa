package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

public record AtributosDeRastreio(String traceParent, String traceState, String correlationId) {
}

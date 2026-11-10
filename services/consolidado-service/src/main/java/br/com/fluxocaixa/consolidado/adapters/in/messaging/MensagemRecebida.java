package br.com.fluxocaixa.consolidado.adapters.in.messaging;

import java.util.Map;

public record MensagemRecebida(String corpo, Map<String, String> atributos, String receiptHandle) {
}

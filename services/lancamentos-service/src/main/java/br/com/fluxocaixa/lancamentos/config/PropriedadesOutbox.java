package br.com.fluxocaixa.lancamentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.outbox")
public record PropriedadesOutbox(
        long intervaloMs,
        int lote,
        int backoffMaximoSegundos,
        Duration retencao,
        Duration adiamentoCircuito
) {

    public PropriedadesOutbox {
        if (intervaloMs <= 0) {
            intervaloMs = 500;
        }
        if (lote <= 0) {
            lote = 100;
        }
        if (backoffMaximoSegundos <= 0) {
            backoffMaximoSegundos = 60;
        }
        if (retencao == null || retencao.isZero() || retencao.isNegative()) {
            retencao = Duration.ofDays(7);
        }
        if (adiamentoCircuito == null || adiamentoCircuito.isZero() || adiamentoCircuito.isNegative()) {
            adiamentoCircuito = Duration.ofSeconds(30);
        }
    }
}

package br.com.fluxocaixa.lancamentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox")
public record PropriedadesOutbox(long intervaloMs, int lote, int backoffMaximoSegundos) {

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
    }
}

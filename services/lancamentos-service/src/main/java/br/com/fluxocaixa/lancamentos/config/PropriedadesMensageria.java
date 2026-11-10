package br.com.fluxocaixa.lancamentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging")
public record PropriedadesMensageria(String topicArn) {
}

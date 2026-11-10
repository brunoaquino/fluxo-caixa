package br.com.fluxocaixa.consolidado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record PropriedadesAplicacao(Security security, Messaging messaging, Aws aws, Cache cache) {

    public record Security(String comercianteClaim, String scopePrefix, Jwt jwt) {

        public record Jwt(String issuerUri, String jwkSetUri) {
        }
    }

    public record Messaging(String queueUrl, boolean consumoHabilitado, int waitTimeSeconds, int maxMessages) {
    }

    public record Aws(String region, String endpointUrl) {
    }

    public record Cache(Duration ttl) {
    }
}

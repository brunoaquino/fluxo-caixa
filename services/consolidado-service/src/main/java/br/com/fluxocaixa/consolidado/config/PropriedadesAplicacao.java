package br.com.fluxocaixa.consolidado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record PropriedadesAplicacao(
        Security security,
        Messaging messaging,
        Aws aws,
        Cache cache,
        Datasource datasource,
        Resilience resilience) {

    public PropriedadesAplicacao {
        if (datasource == null) {
            datasource = new Datasource(null, null);
        }
        if (resilience == null) {
            resilience = new Resilience(false);
        }
        if (security == null) {
            security = new Security(null, null, null, false, List.of());
        }
    }

    public record Security(
            String comercianteClaim,
            String scopePrefix,
            Jwt jwt,
            boolean swaggerPublico,
            List<String> corsAllowedOrigins) {

        public Security {
            if (corsAllowedOrigins == null) {
                corsAllowedOrigins = List.of();
            } else {
                corsAllowedOrigins = corsAllowedOrigins.stream()
                        .filter(origem -> origem != null && !origem.isBlank())
                        .map(String::trim)
                        .toList();
            }
        }

        public record Jwt(String issuerUri, String jwkSetUri) {
        }
    }

    public record Messaging(String queueUrl, boolean consumoHabilitado, int waitTimeSeconds, int maxMessages) {
    }

    public record Aws(String region, String endpointUrl) {
    }

    public record Cache(Duration ttl) {
    }

    public record Datasource(String secretArn, String readerUrl) {

        public Datasource {
            secretArn = limpar(secretArn);
            readerUrl = limpar(readerUrl);
        }

        public boolean usaSegredo() {
            return secretArn != null;
        }

        public boolean temLeitura() {
            return readerUrl != null;
        }

        private static String limpar(String valor) {
            if (valor == null || valor.isBlank()) {
                return null;
            }
            return valor.trim();
        }
    }

    public record Resilience(boolean rateLimiterHabilitado) {
    }
}

package br.com.fluxocaixa.lancamentos.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sns.SnsClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class AwsConfigTest {

    private final AwsConfig config = new AwsConfig();

    @Test
    void endpointAusenteUsaOEndpointPadraoDaAws() {
        assertSemOverride(null);
        assertSemOverride("");
        assertSemOverride("   ");
    }

    @Test
    void endpointPreenchidoSobrescreveEUsaCredencialEstatica() {
        try (SnsClient client = config.snsClient("sa-east-1", "http://localhost:4566", "test", "test")) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .contains(URI.create("http://localhost:4566"));
            assertThat(client.serviceClientConfiguration().credentialsProvider().getClass().getName())
                    .contains("StaticCredentialsProvider");
        }
    }

    private void assertSemOverride(String endpoint) {
        try (SnsClient client = config.snsClient("sa-east-1", endpoint, "test", "test")) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(client.serviceClientConfiguration().credentialsProvider().getClass().getName())
                    .doesNotContain("StaticCredentialsProvider");
        }
    }
}

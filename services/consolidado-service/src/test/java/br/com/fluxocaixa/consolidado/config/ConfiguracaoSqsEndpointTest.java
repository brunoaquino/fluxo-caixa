package br.com.fluxocaixa.consolidado.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguracaoSqsEndpointTest {

    private final ConfiguracaoSqs config = new ConfiguracaoSqs();

    @Test
    void endpointAusenteUsaOEndpointPadraoDaAws() {
        assertSemOverride(null);
        assertSemOverride("");
        assertSemOverride("   ");
    }

    @Test
    void endpointPreenchidoSobrescreveEUsaCredencialEstatica() {
        try (SqsClient client = config.sqsClient(propriedades("http://localhost:4566"))) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                    .contains(URI.create("http://localhost:4566"));
            assertThat(client.serviceClientConfiguration().credentialsProvider().getClass().getName())
                    .contains("StaticCredentialsProvider");
        }
    }

    @Test
    void socketDoLongPollingFicaAcimaDeVinteSegundos() {
        assertThat(ConfiguracaoSqs.TIMEOUT_SOCKET).isGreaterThan(ConfiguracaoSqs.ESPERA_LONG_POLL);
        assertThat(ConfiguracaoSqs.ESPERA_LONG_POLL).isEqualTo(Duration.ofSeconds(20));
        assertThat(ConfiguracaoSqs.TIMEOUT_TENTATIVA).isGreaterThan(ConfiguracaoSqs.TIMEOUT_SOCKET);
        assertThat(ConfiguracaoSqs.TIMEOUT_CHAMADA).isGreaterThan(ConfiguracaoSqs.TIMEOUT_TENTATIVA);
        assertThat(ConfiguracaoSqs.clienteHttp()).isNotNull();
    }

    private void assertSemOverride(String endpoint) {
        try (SqsClient client = config.sqsClient(propriedades(endpoint))) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(client.serviceClientConfiguration().credentialsProvider().getClass().getName())
                    .contains("DefaultCredentialsProvider");
        }
    }

    private static PropriedadesAplicacao propriedades(String endpoint) {
        return new PropriedadesAplicacao(
                new PropriedadesAplicacao.Security(
                        "comerciante_id",
                        "",
                        new PropriedadesAplicacao.Security.Jwt("http://localhost/issuer", ""),
                        false,
                        List.of()),
                new PropriedadesAplicacao.Messaging(
                        "http://localhost:4566/000000000000/fila", true, 20, 10),
                new PropriedadesAplicacao.Aws("us-east-1", endpoint),
                new PropriedadesAplicacao.Cache(Duration.ofSeconds(30)),
                new PropriedadesAplicacao.Datasource(null, null),
                new PropriedadesAplicacao.Resilience(false));
    }
}

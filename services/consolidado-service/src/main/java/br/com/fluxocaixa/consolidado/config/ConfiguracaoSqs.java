package br.com.fluxocaixa.consolidado.config;

import br.com.fluxocaixa.consolidado.adapters.in.messaging.ConsumidorSqsLancamentos;
import br.com.fluxocaixa.consolidado.adapters.in.messaging.MetricasConsumo;
import br.com.fluxocaixa.consolidado.adapters.in.messaging.ProcessadorEventoLancamento;
import br.com.fluxocaixa.consolidado.adapters.in.messaging.TradutorEventoLancamento;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;
import java.time.Duration;

@Configuration
public class ConfiguracaoSqs {

    @Bean
    @ConditionalOnProperty(prefix = "app.messaging", name = "consumo-habilitado", havingValue = "true")
    SqsClient sqsClient(PropriedadesAplicacao propriedades) {
        String regiao = propriedades.aws().region();
        if (regiao == null || regiao.isBlank()) {
            regiao = "sa-east-1";
        }
        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(regiao))
                .overrideConfiguration(config -> config
                        .apiCallTimeout(Duration.ofSeconds(35))
                        .apiCallAttemptTimeout(Duration.ofSeconds(32)));
        String endpoint = propriedades.aws().endpointUrl();
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    System.getenv().getOrDefault("AWS_ACCESS_KEY_ID", "test"),
                    System.getenv().getOrDefault("AWS_SECRET_ACCESS_KEY", "test"))));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.messaging", name = "consumo-habilitado", havingValue = "true")
    ProcessadorEventoLancamento.ConfirmacaoFila confirmacaoFila(SqsClient sqsClient, PropriedadesAplicacao propriedades) {
        String fila = urlFila(propriedades);
        return receiptHandle -> sqsClient.deleteMessage(pedido -> pedido.queueUrl(fila).receiptHandle(receiptHandle));
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.messaging", name = "consumo-habilitado", havingValue = "true")
    ConsumidorSqsLancamentos consumidorSqsLancamentos(
            SqsClient sqsClient,
            PropriedadesAplicacao propriedades,
            TradutorEventoLancamento tradutor,
            br.com.fluxocaixa.consolidado.application.port.in.AplicarLancamentoUseCase aplicarLancamento,
            MetricasConsumo metricas,
            ProcessadorEventoLancamento.ConfirmacaoFila confirmacao,
            ObservationRegistry observationRegistry) {
        ProcessadorEventoLancamento processador = new ProcessadorEventoLancamento(
                tradutor,
                aplicarLancamento,
                metricas,
                confirmacao);
        return new ConsumidorSqsLancamentos(sqsClient, propriedades, processador, observationRegistry);
    }

    private static String urlFila(PropriedadesAplicacao propriedades) {
        String fila = propriedades.messaging().queueUrl();
        if (fila == null || fila.isBlank()) {
            throw new IllegalStateException("CONSOLIDADO_QUEUE_URL é obrigatória quando o consumo está habilitado");
        }
        return fila;
    }
}

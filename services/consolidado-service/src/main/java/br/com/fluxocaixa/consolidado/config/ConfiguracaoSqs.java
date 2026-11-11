package br.com.fluxocaixa.consolidado.config;

import br.com.fluxocaixa.consolidado.adapters.in.messaging.ConsumidorSqsLancamentos;
import br.com.fluxocaixa.consolidado.adapters.in.messaging.MetricasConsumo;
import br.com.fluxocaixa.consolidado.adapters.in.messaging.ProcessadorEventoLancamento;
import br.com.fluxocaixa.consolidado.adapters.in.messaging.TradutorEventoLancamento;
import br.com.fluxocaixa.consolidado.observabilidade.RastreioMensagem;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.net.URI;
import java.time.Duration;

@Configuration
public class ConfiguracaoSqs {

    /** Long polling da fila. O socket do cliente HTTP tem de ser maior que este valor. */
    static final Duration ESPERA_LONG_POLL = Duration.ofSeconds(20);
    /** Acima de {@link #ESPERA_LONG_POLL}. O url-connection corta o socket se o timeout for menor ou igual. */
    static final Duration TIMEOUT_SOCKET = Duration.ofSeconds(25);
    static final Duration TIMEOUT_CONEXAO = Duration.ofSeconds(2);
    static final Duration TIMEOUT_TENTATIVA = Duration.ofSeconds(32);
    static final Duration TIMEOUT_CHAMADA = Duration.ofSeconds(35);
    static final String REGIAO_PADRAO = "us-east-1";

    static UrlConnectionHttpClient.Builder clienteHttp() {
        return UrlConnectionHttpClient.builder()
                .connectionTimeout(TIMEOUT_CONEXAO)
                .socketTimeout(TIMEOUT_SOCKET);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.messaging", name = "consumo-habilitado", havingValue = "true")
    SqsClient sqsClient(PropriedadesAplicacao propriedades) {
        String regiao = propriedades.aws().region();
        if (regiao == null || regiao.isBlank()) {
            regiao = REGIAO_PADRAO;
        }
        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(regiao))
                .httpClientBuilder(clienteHttp())
                .overrideConfiguration(config -> config
                        .apiCallTimeout(TIMEOUT_CHAMADA)
                        .apiCallAttemptTimeout(TIMEOUT_TENTATIVA));
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
            RetryRegistry retries,
            BulkheadRegistry bulkheads,
            Tracer tracer,
            RastreioMensagem rastreio) {
        ProcessadorEventoLancamento processador = new ProcessadorEventoLancamento(
                tradutor,
                aplicarLancamento,
                metricas,
                confirmacao,
                retries.retry("banco-consumo"),
                bulkheads.bulkhead("sqs-consumo"),
                tracer);
        return new ConsumidorSqsLancamentos(sqsClient, propriedades, processador, rastreio);
    }

    private static String urlFila(PropriedadesAplicacao propriedades) {
        String fila = propriedades.messaging().queueUrl();
        if (fila == null || fila.isBlank()) {
            throw new IllegalStateException("CONSOLIDADO_QUEUE_URL é obrigatória quando o consumo está habilitado");
        }
        return fila;
    }
}

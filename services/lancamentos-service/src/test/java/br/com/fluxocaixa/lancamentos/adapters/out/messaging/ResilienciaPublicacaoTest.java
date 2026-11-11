package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import br.com.fluxocaixa.lancamentos.ContainersDeTeste;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "resilience4j.retry.instances.publicacaoSns.maxAttempts=4",
        "resilience4j.retry.instances.publicacaoSns.waitDuration=1ms",
        "resilience4j.timelimiter.instances.publicacaoSns.timeoutDuration=200ms",
        "resilience4j.circuitbreaker.instances.publicacaoSns.minimumNumberOfCalls=2",
        "resilience4j.circuitbreaker.instances.publicacaoSns.slidingWindowSize=4",
        "resilience4j.circuitbreaker.instances.publicacaoSns.failureRateThreshold=50",
        "resilience4j.circuitbreaker.instances.publicacaoSns.waitDurationInOpenState=30s",
        "app.outbox.intervalo-ms=3600000"
})
class ResilienciaPublicacaoTest {

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("DB_URL", () -> ContainersDeTeste.jdbcUrl("resiliencia"));
        registro.add("DB_USERNAME", ContainersDeTeste.POSTGRES::getUsername);
        registro.add("DB_PASSWORD", ContainersDeTeste.POSTGRES::getPassword);
        registro.add("spring.datasource.url", () -> ContainersDeTeste.jdbcUrl("resiliencia"));
        registro.add("spring.datasource.username", ContainersDeTeste.POSTGRES::getUsername);
        registro.add("spring.datasource.password", ContainersDeTeste.POSTGRES::getPassword);
        registro.add("AWS_ENDPOINT_URL", () -> ContainersDeTeste.LOCALSTACK.getEndpoint().toString());
        registro.add("AWS_REGION", ContainersDeTeste.LOCALSTACK::getRegion);
        registro.add("AWS_ACCESS_KEY_ID", ContainersDeTeste.LOCALSTACK::getAccessKey);
        registro.add("AWS_SECRET_ACCESS_KEY", ContainersDeTeste.LOCALSTACK::getSecretKey);
        registro.add("LANCAMENTOS_TOPIC_ARN", () -> ContainersDeTeste.topicArn);
        registro.add("JWT_ISSUER_URI", () -> "http://localhost/realms/fluxo-caixa");
        registro.add("JWT_JWK_SET_URI", () -> "http://127.0.0.1:9/jwks");
        registro.add("management.otlp.tracing.export.enabled", () -> "false");
        registro.add("management.otlp.metrics.export.enabled", () -> "false");
        registro.add("management.otlp.logging.export.enabled", () -> "false");
    }

    @Autowired
    private SnsPublicador publicador;

    @Autowired
    private CircuitBreakerRegistry circuitos;

    @MockitoBean
    private SnsClient snsClient;

    @BeforeEach
    void zeraCircuito() {
        circuitos.circuitBreaker(SnsPublicador.INSTANCIA).reset();
    }

    @Test
    void retryDoAspectoRepeteAtePublicar() {
        when(snsClient.publish(any(PublishRequest.class)))
                .thenThrow(sdk("falha-1"))
                .thenThrow(sdk("falha-2"))
                .thenThrow(sdk("falha-3"))
                .thenReturn(PublishResponse.builder().messageId("ok").build());

        publicador.publicar("{}", "LancamentoRegistrado", rastreio());

        verify(snsClient, times(4)).publish(any(PublishRequest.class));
    }

    @Test
    void timeLimiterEstouraAntesDoBloqueio() {
        when(snsClient.publish(any(PublishRequest.class))).thenAnswer(invocacao -> {
            Thread.sleep(5_000);
            return PublishResponse.builder().messageId("lento").build();
        });

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> publicador.publicar("{}", "LancamentoRegistrado", rastreio()))
                .isInstanceOf(TimeoutDePublicacaoException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    void circuitoAbreEBarraAProximaChamada() {
        when(snsClient.publish(any(PublishRequest.class))).thenThrow(sdk("indisponivel"));

        assertThatThrownBy(() -> publicador.publicar("{}", "LancamentoRegistrado", rastreio()))
                .isInstanceOf(SdkClientException.class);
        assertThatThrownBy(() -> publicador.publicar("{}", "LancamentoRegistrado", rastreio()))
                .isInstanceOf(SdkClientException.class);
        assertThatThrownBy(() -> publicador.publicar("{}", "LancamentoRegistrado", rastreio()))
                .isInstanceOf(CallNotPermittedException.class);

        verify(snsClient, times(8)).publish(any(PublishRequest.class));
    }

    private static SdkClientException sdk(String mensagem) {
        return SdkClientException.builder().message(mensagem).build();
    }

    private static AtributosDeRastreio rastreio() {
        return new AtributosDeRastreio(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                null,
                "33333333-3333-4333-8333-333333333333",
                null
        );
    }
}

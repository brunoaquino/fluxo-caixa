package br.com.fluxocaixa.consolidado;

import br.com.fluxocaixa.consolidado.config.JwtDecoderTesteConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(JwtDecoderTesteConfig.class)
class ConsolidadoIntegracaoTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("consolidado")
            .withUsername("consolidado")
            .withPassword("consolidado")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer("localstack/localstack:4.7")
            .withServices("sqs")
            .withStartupTimeout(Duration.ofMinutes(3));

    private static volatile String urlFila;

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.aws.endpoint-url", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("app.aws.region", () -> "sa-east-1");
        registry.add("app.messaging.consumo-habilitado", () -> "true");
        registry.add("app.messaging.wait-time-seconds", () -> "1");
        registry.add("app.messaging.max-messages", () -> "10");
        registry.add("app.messaging.queue-url", ConsolidadoIntegracaoTest::urlFila);
        registry.add("management.tracing.export.otlp.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void probesNaoDependemDaFila() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    void mesmaMensagemDuasVezesContabilizaUma() {
        UUID comerciante = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String corpo = evento(eventId, UUID.randomUUID(), comerciante, "CREDITO", "150.00", "2026-11-10", null);

        enviar(corpo);
        aguardarDia(comerciante, "2026-11-10", 1, 150, 0, 150, 150);

        double duplicados = meterRegistry.counter("consolidado.eventos.duplicados").count();
        enviar(corpo);
        await().atMost(40, SECONDS).pollInterval(Duration.ofMillis(200)).until(
                () -> meterRegistry.counter("consolidado.eventos.duplicados").count() > duplicados);

        aguardarDia(comerciante, "2026-11-10", 1, 150, 0, 150, 150);
        Long processados = jdbc.queryForObject(
                "select count(*) from eventos_processados where event_id = ?",
                Long.class,
                eventId);
        org.assertj.core.api.Assertions.assertThat(processados).isEqualTo(1L);
    }

    @Test
    void creditoDebitoEstornoESaldoAcumulado() throws Exception {
        UUID comerciante = UUID.randomUUID();
        UUID credito = UUID.randomUUID();
        UUID debito = UUID.randomUUID();
        enviar(evento(UUID.randomUUID(), credito, comerciante, "CREDITO", "200.00", "2026-11-10", null));
        enviar(evento(UUID.randomUUID(), debito, comerciante, "DEBITO", "50.00", "2026-11-10", null));
        enviar(evento(UUID.randomUUID(), UUID.randomUUID(), comerciante, "CREDITO", "50.00", "2026-11-10", debito));
        enviar(evento(UUID.randomUUID(), UUID.randomUUID(), comerciante, "CREDITO", "10.00", "2026-11-11", null));

        aguardarDia(comerciante, "2026-11-10", 3, 250, 50, 200, 200);
        aguardarDia(comerciante, "2026-11-11", 1, 10, 0, 10, 210);

        mockMvc.perform(get("/api/v1/consolidados/diario/2026-11-12").with(token(comerciante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCreditos").value(closeTo(0.0, 0.001)))
                .andExpect(jsonPath("$.saldo").value(closeTo(0.0, 0.001)))
                .andExpect(jsonPath("$.saldoAcumulado").value(closeTo(210.0, 0.001)))
                .andExpect(jsonPath("$.quantidadeLancamentos").value(0))
                .andExpect(jsonPath("$.atualizadoEm").value(nullValue()));

        mockMvc.perform(get("/api/v1/consolidados/diario")
                        .param("inicio", "2026-11-10")
                        .param("fim", "2026-11-12")
                        .with(token(comerciante)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias.length()").value(3))
                .andExpect(jsonPath("$.dias[0].saldo").value(closeTo(200.0, 0.001)))
                .andExpect(jsonPath("$.dias[0].saldoAcumulado").value(closeTo(200.0, 0.001)))
                .andExpect(jsonPath("$.dias[1].saldoAcumulado").value(closeTo(210.0, 0.001)))
                .andExpect(jsonPath("$.dias[2].quantidadeLancamentos").value(0))
                .andExpect(jsonPath("$.dias[2].saldoAcumulado").value(closeTo(210.0, 0.001)));
    }

    private void aguardarDia(
            UUID comerciante,
            String data,
            int quantidade,
            double creditos,
            double debitos,
            double saldo,
            double acumulado) {
        await().atMost(40, SECONDS).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/consolidados/diario/" + data).with(token(comerciante)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.quantidadeLancamentos").value(quantidade))
                        .andExpect(jsonPath("$.totalCreditos").value(closeTo(creditos, 0.001)))
                        .andExpect(jsonPath("$.totalDebitos").value(closeTo(debitos, 0.001)))
                        .andExpect(jsonPath("$.saldo").value(closeTo(saldo, 0.001)))
                        .andExpect(jsonPath("$.saldoAcumulado").value(closeTo(acumulado, 0.001))));
    }

    private static void enviar(String corpo) {
        try (SqsClient cliente = cliente()) {
            cliente.sendMessage(pedido -> pedido
                    .queueUrl(urlFila())
                    .messageBody(corpo)
                    .messageAttributes(Map.of(
                            "eventType",
                            MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue("LancamentoRegistrado")
                                    .build())));
        }
    }

    private static SqsClient cliente() {
        return SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of("sa-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(),
                        LOCALSTACK.getSecretKey())))
                .build();
    }

    private static String urlFila() {
        if (urlFila != null) {
            return urlFila;
        }
        synchronized (ConsolidadoIntegracaoTest.class) {
            if (urlFila == null) {
                try (SqsClient cliente = cliente()) {
                    urlFila = cliente.createQueue(pedido -> pedido.queueName("consolidado-lancamentos-queue")).queueUrl();
                }
            }
            return urlFila;
        }
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor token(UUID comerciante) {
        return jwt().jwt(jwt -> jwt.claim("comerciante_id", comerciante.toString()))
                .authorities(new SimpleGrantedAuthority("SCOPE_consolidado:read"));
    }

    private static String evento(
            UUID eventId,
            UUID lancamentoId,
            UUID comerciante,
            String tipo,
            String valor,
            String data,
            UUID estorno) {
        String estornoJson = estorno == null ? "null" : "\"" + estorno + "\"";
        return """
                {
                  "eventId": "%s",
                  "eventType": "LancamentoRegistrado",
                  "eventVersion": 1,
                  "occurredAt": "2026-11-10T18:00:00.123Z",
                  "lancamentoId": "%s",
                  "comercianteId": "%s",
                  "tipo": "%s",
                  "valor": "%s",
                  "moeda": "BRL",
                  "dataLancamento": "%s",
                  "lancamentoEstornadoId": %s
                }
                """.formatted(eventId, lancamentoId, comerciante, tipo, valor, data, estornoJson);
    }
}

package br.com.fluxocaixa.lancamentos;

import com.zaxxer.hikari.HikariDataSource;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class FluxoLancamentoIntegracaoTest {

    private static final UUID COMERCIANTE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OUTRO_COMERCIANTE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    static final List<Message> mensagens = new ArrayList<>();

    static {
        if (ContainersDeTeste.sqs == null) {
            throw new IllegalStateException("LocalStack não subiu");
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("DB_URL", ContainersDeTeste.POSTGRES::getJdbcUrl);
        registro.add("DB_USERNAME", ContainersDeTeste.POSTGRES::getUsername);
        registro.add("DB_PASSWORD", ContainersDeTeste.POSTGRES::getPassword);
        registro.add("spring.datasource.url", ContainersDeTeste.POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", ContainersDeTeste.POSTGRES::getUsername);
        registro.add("spring.datasource.password", ContainersDeTeste.POSTGRES::getPassword);
        registro.add("AWS_ENDPOINT_URL", () -> ContainersDeTeste.LOCALSTACK.getEndpoint().toString());
        registro.add("AWS_REGION", ContainersDeTeste.LOCALSTACK::getRegion);
        registro.add("AWS_ACCESS_KEY_ID", ContainersDeTeste.LOCALSTACK::getAccessKey);
        registro.add("AWS_SECRET_ACCESS_KEY", ContainersDeTeste.LOCALSTACK::getSecretKey);
        registro.add("LANCAMENTOS_TOPIC_ARN", () -> ContainersDeTeste.topicArn);
        registro.add("app.messaging.topic-arn", () -> ContainersDeTeste.topicArn);
        registro.add("JWT_ISSUER_URI", () -> "http://localhost/realms/fluxo-caixa");
        registro.add("JWT_JWK_SET_URI", () -> "http://127.0.0.1:9/jwks");
        registro.add("app.outbox.intervalo-ms", () -> "200");
        registro.add("app.outbox.lote", () -> "100");
        registro.add("app.outbox.backoff-maximo-segundos", () -> "60");
        registro.add("app.outbox.adiamento-circuito", () -> "5s");
        registro.add("management.otlp.tracing.export.enabled", () -> "false");
        registro.add("management.otlp.metrics.export.enabled", () -> "false");
        registro.add("management.otlp.logging.export.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private javax.sql.DataSource dataSource;

    @Autowired
    private CircuitBreakerRegistry circuitos;

    @Autowired
    private RetryRegistry retentativas;

    @Autowired
    private TimeLimiterRegistry limitadores;

    @Autowired
    private MeterRegistry medidores;

    @Test
    void publicaLancamentoNoSnsEEntregaOJsonNaFila() throws Exception {
        String resposta = mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CREDITO","valor":150.00,"dataLancamento":"2026-11-10","descricao":"Venda balcão","categoria":"VENDAS"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value("150.00"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = jsonMapper.readTree(resposta).get("id").asString();

        JsonNode evento = aguardarEvento(id);
        assertThat(evento.get("eventType").asString()).isEqualTo("LancamentoRegistrado");
        assertThat(evento.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(evento.get("comercianteId").asString()).isEqualTo(COMERCIANTE.toString());
        assertThat(evento.get("tipo").asString()).isEqualTo("CREDITO");
        assertThat(evento.get("valor").asString()).isEqualTo("150.00");
        assertThat(evento.get("moeda").asString()).isEqualTo("BRL");
        assertThat(evento.get("dataLancamento").asString()).isEqualTo("2026-11-10");
        assertThat(evento.get("lancamentoEstornadoId").isNull()).isTrue();
        assertThat(evento.get("lancamentoId").asString()).isEqualTo(id);
    }

    @Test
    void idempotenciaNaoDuplicaLancamento() throws Exception {
        String chave = UUID.randomUUID().toString();
        String corpo = """
                {"tipo":"DEBITO","valor":40.50,"dataLancamento":"2026-11-09","descricao":"Fornecedor","categoria":"COMPRAS"}
                """;
        String primeira = mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String segunda = mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(jsonMapper.readTree(segunda).get("id").asString())
                .isEqualTo(jsonMapper.readTree(primeira).get("id").asString());

        mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .header("Idempotency-Key", chave)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"DEBITO","valor":99.00,"dataLancamento":"2026-11-09","descricao":"Outro","categoria":"COMPRAS"}
                                """))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/lancamentos")
                        .with(token("lancamentos:read"))
                        .param("data", "2026-11-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void estornoPublicaTipoOpostoENaoPermiteSegundoEstorno() throws Exception {
        String criado = mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CREDITO","valor":80.00,"dataLancamento":"2026-11-08","descricao":"Serviço"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String original = jsonMapper.readTree(criado).get("id").asString();

        String estorno = mockMvc.perform(post("/api/v1/lancamentos/" + original + "/estornos")
                        .with(token("lancamentos:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"motivo":"Cancelado"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("DEBITO"))
                .andExpect(jsonPath("$.valor").value("80.00"))
                .andExpect(jsonPath("$.dataLancamento").value("2026-11-08"))
                .andExpect(jsonPath("$.lancamentoEstornadoId").value(original))
                .andReturn().getResponse().getContentAsString();
        String idEstorno = jsonMapper.readTree(estorno).get("id").asString();

        JsonNode evento = aguardarEvento(idEstorno);
        assertThat(evento.get("tipo").asString()).isEqualTo("DEBITO");
        assertThat(evento.get("valor").asString()).isEqualTo("80.00");
        assertThat(evento.get("lancamentoEstornadoId").asString()).isEqualTo(original);

        mockMvc.perform(post("/api/v1/lancamentos/" + original + "/estornos")
                        .with(token("lancamentos:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/lancamentos/" + idEstorno + "/estornos")
                        .with(token("lancamentos:write")))
                .andExpect(status().isConflict());
    }

    @Test
    void lancamentoDeOutroComercianteResponde404() throws Exception {
        String criado = mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"DEBITO","valor":12.00,"dataLancamento":"2026-11-07","descricao":"Só meu"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = jsonMapper.readTree(criado).get("id").asString();

        mockMvc.perform(get("/api/v1/lancamentos/" + id).with(token(OUTRO_COMERCIANTE, "lancamentos:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Lançamento não encontrado"));
        mockMvc.perform(post("/api/v1/lancamentos/" + id + "/estornos")
                        .with(token(OUTRO_COMERCIANTE, "lancamentos:write")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Lançamento não encontrado"));
    }

    @Test
    void mensagemCarregaTraceDaRequisicaoECorrelationId() throws Exception {
        String correlation = "22222222-2222-4222-8222-222222222222";
        String resposta = mockMvc.perform(post("/api/v1/lancamentos")
                        .with(token("lancamentos:write"))
                        .header("traceparent", "00-" + TRACE_ID + "-00f067aa0ba902b7-01")
                        .header("tracestate", "vendor=abc")
                        .header("X-Correlation-Id", correlation)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tipo":"CREDITO","valor":15.00,"dataLancamento":"2026-11-06","descricao":"Com trace"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", correlation))
                .andReturn().getResponse().getContentAsString();
        String id = jsonMapper.readTree(resposta).get("id").asString();

        Message mensagem = aguardarMensagem(id);
        String traceparent = mensagem.messageAttributes().get("traceparent").stringValue();
        assertThat(traceparent).matches("00-" + TRACE_ID + "-[0-9a-f]{16}-[0-9a-f]{2}");
        assertThat(mensagem.messageAttributes().get("correlationId").stringValue()).isEqualTo(correlation);
        assertThat(mensagem.messageAttributes().get("eventType").stringValue()).isEqualTo("LancamentoRegistrado");

        String gravado = jdbc.queryForObject(
                "select trace_parent from outbox_eventos where aggregate_id = ?",
                String.class,
                UUID.fromString(id)
        );
        String correlationGravado = jdbc.queryForObject(
                "select correlation_id from outbox_eventos where aggregate_id = ?",
                String.class,
                UUID.fromString(id)
        );
        assertThat(gravado).contains(TRACE_ID);
        assertThat(traceparent).isNotEqualTo(gravado);
        assertThat(correlationGravado).isEqualTo(correlation);
        if (mensagem.messageAttributes().containsKey("tracestate")) {
            assertThat(mensagem.messageAttributes().get("tracestate").stringValue()).contains("vendor=abc");
        }
    }

    @Test
    void circuitoAbertoAdiaOLoteSemPerderEventoENaoDerrubaReadiness() throws Exception {
        var circuito = circuitos.circuitBreaker("publicacaoSns");
        circuito.transitionToOpenState();
        try {
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components.circuitBreakers").doesNotExist());
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components.circuitBreakers.status").value("CIRCUIT_OPEN"));

            String criado = mockMvc.perform(post("/api/v1/lancamentos")
                            .with(token("lancamentos:write"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"tipo":"CREDITO","valor":9.00,"dataLancamento":"2026-11-05","descricao":"Durante circuito"}
                                    """))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            String id = jsonMapper.readTree(criado).get("id").asString();

            await().atMost(Duration.ofSeconds(8)).until(() -> jdbc.queryForObject(
                    """
                    select proxima_tentativa_em > CURRENT_TIMESTAMP + interval '2 seconds'
                       and publicado_em is null
                    from outbox_eventos where aggregate_id = ?
                    """,
                    Boolean.class,
                    UUID.fromString(id)
            ));

            circuito.transitionToClosedState();
            jdbc.update(
                    "update outbox_eventos set proxima_tentativa_em = CURRENT_TIMESTAMP where aggregate_id = ?",
                    UUID.fromString(id)
            );
            aguardarMensagem(id);
        } finally {
            circuito.reset();
        }
    }

    @Test
    void semReplicaOPoolEUnicoEAResilienciaEstaNoMicrometer() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        var retentativa = retentativas.retry("publicacaoSns").getRetryConfig();
        assertThat(retentativa.getMaxAttempts()).isEqualTo(3);
        var falha = io.github.resilience4j.core.functions.Either.<Throwable, String>left(new RuntimeException("falha"));
        long primeira = retentativa.<String>getIntervalBiFunction().apply(1, falha);
        long terceira = retentativa.<String>getIntervalBiFunction().apply(3, falha);
        assertThat(primeira).isBetween(25L, 75L);
        assertThat(terceira).isGreaterThan(primeira);
        assertThat(limitadores.timeLimiter("publicacaoSns").getTimeLimiterConfig().getTimeoutDuration())
                .isEqualTo(Duration.ofSeconds(1));
        assertThat(medidores.find("resilience4j.circuitbreaker.state").tag("name", "publicacaoSns")).isNotNull();
    }

    @Test
    void logJsonTemTraceIdECorrelationIdSemToken(CapturedOutput saida) throws Exception {
        String correlation = "33333333-3333-4333-8333-333333333333";
        mockMvc.perform(get("/api/v1/lancamentos")
                        .with(token("lancamentos:read"))
                        .header("X-Correlation-Id", correlation)
                        .param("data", "2026-01-01"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/liveness")
                        .header("Authorization", "Bearer super-secreto-token-xyz")
                        .header("X-Correlation-Id", correlation))
                .andExpect(status().isUnauthorized());

        assertThat(saida.getAll())
                .contains(correlation)
                .contains("\"traceId\"")
                .contains(COMERCIANTE.toString())
                .doesNotContain("super-secreto-token-xyz");
    }

    @Test
    void explicaConsultasCriticas() throws Exception {
        UUID comerciante = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        jdbc.update("delete from lancamentos where comerciante_id = ?", comerciante);
        jdbc.update("""
                insert into lancamentos (
                    id, comerciante_id, tipo, valor, moeda, data_lancamento, descricao, categoria, criado_em
                )
                select gen_random_uuid(), ?, 'CREDITO', 10.00, 'BRL',
                       date '2026-11-10' - (g % 10), 'carga de plano', 'PERF',
                       timestamptz '2026-11-10 12:00:00+00' - (g || ' seconds')::interval
                from generate_series(1, 4000) g
                """, comerciante);
        jdbc.update("""
                insert into outbox_eventos (
                    id, aggregate_id, event_type, payload, criado_em, tentativas, proxima_tentativa_em
                )
                select gen_random_uuid(), gen_random_uuid(), 'LancamentoRegistrado', '{"perf":true}'::jsonb,
                       timestamptz '2026-11-10 12:00:00+00' + (g || ' milliseconds')::interval,
                       0, timestamptz '2099-06-01 00:00:00+00'
                from generate_series(1, 1500) g
                """);
        jdbc.update("""
                insert into outbox_eventos (
                    id, aggregate_id, event_type, payload, criado_em, tentativas, proxima_tentativa_em
                )
                select gen_random_uuid(), gen_random_uuid(), 'LancamentoRegistrado', '{"perf":true}'::jsonb,
                       timestamptz '2026-11-10 18:00:00+00' + (g || ' milliseconds')::interval,
                       0, timestamptz '2099-01-01 00:00:00+00'
                from generate_series(1, 30) g
                """);
        jdbc.execute("ANALYZE lancamentos");
        jdbc.execute("ANALYZE outbox_eventos");

        String listagem = plano("""
                explain (analyze, buffers)
                select id, comerciante_id, tipo, valor, moeda, data_lancamento, descricao, categoria,
                       lancamento_estornado_id, criado_em
                from lancamentos
                where comerciante_id = '%s' and data_lancamento = date '2026-11-10'
                order by criado_em asc
                limit 50 offset 0
                """.formatted(comerciante));
        String contagem = plano("""
                explain (analyze, buffers)
                select count(*) from lancamentos
                where comerciante_id = '%s' and data_lancamento = date '2026-11-10'
                """.formatted(comerciante));
        String relay = plano("""
                explain (analyze, buffers)
                select id, aggregate_id, event_type, payload::text, tentativas, trace_parent, trace_state, correlation_id
                from outbox_eventos
                where publicado_em is null
                  and proxima_tentativa_em >= timestamptz '2099-01-01 00:00:00+00'
                  and proxima_tentativa_em <= timestamptz '2099-01-02 00:00:00+00'
                order by criado_em
                limit 100
                for update skip locked
                """);
        String limpeza = plano("""
                explain (analyze, buffers)
                delete from outbox_eventos
                where publicado_em is not null and publicado_em < timestamptz '2026-11-01 00:00:00+00'
                """);

        jdbc.update("delete from lancamentos where comerciante_id = ?", comerciante);
        jdbc.update("delete from outbox_eventos where payload = '{\"perf\":true}'::jsonb");

        String relatorio = """
                # Listagem
                %s
                # Contagem
                %s
                # Relay
                %s
                # Limpeza
                %s
                """.formatted(listagem, contagem, relay, limpeza);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/planos-consulta.txt"), relatorio);
        System.out.println(relatorio);
        assertThat(listagem).contains("idx_lancamentos_comerciante_data_criado");
        assertThat(contagem.toLowerCase()).doesNotContain("seq scan");
        assertThat(relay).contains("idx_outbox_eventos_pendentes");
    }

    private String plano(String sql) {
        return String.join("\n", jdbc.query(sql, (rs, linha) -> rs.getString(1)));
    }

    private JsonNode aguardarEvento(String lancamentoId) {
        return jsonMapper.readTree(aguardarMensagem(lancamentoId).body());
    }

    private Message aguardarMensagem(String lancamentoId) {
        try {
            await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(300)).until(() -> {
                drenarFila();
                return mensagens.stream().anyMatch(mensagem -> mensagem.body().contains(lancamentoId));
            });
        } catch (ConditionTimeoutException ex) {
            var outbox = jdbc.queryForList(
                    """
                    SELECT id::text, tentativas, publicado_em IS NOT NULL AS publicado,
                           proxima_tentativa_em, CURRENT_TIMESTAMP AS agora_banco,
                           proxima_tentativa_em <= CURRENT_TIMESTAMP AS pronto
                    FROM outbox_eventos
                    """
            );
            throw new AssertionError("Fila sem o lançamento " + lancamentoId + ". Outbox=" + outbox
                    + " mensagensRecebidas=" + mensagens.size(), ex);
        }
        Message mensagem = mensagens.stream()
                .filter(item -> item.body().contains(lancamentoId))
                .findFirst()
                .orElseThrow();
        assertThat(mensagem.messageAttributes()).containsKey("eventType");
        assertThat(mensagem.messageAttributes().get("eventType").stringValue()).isEqualTo("LancamentoRegistrado");
        return mensagem;
    }

    private static void drenarFila() {
        var resposta = ContainersDeTeste.sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(ContainersDeTeste.queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .messageAttributeNames("All")
                .messageSystemAttributeNames(MessageSystemAttributeName.ALL)
                .build());
        for (Message mensagem : resposta.messages()) {
            mensagens.add(mensagem);
            ContainersDeTeste.sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(ContainersDeTeste.queueUrl)
                    .receiptHandle(mensagem.receiptHandle())
                    .build());
        }
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor token(String escopos) {
        return token(COMERCIANTE, escopos);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor token(UUID comerciante, String escopos) {
        return jwt().jwt(token -> token
                .claim("comerciante_id", comerciante.toString())
                .claim("scope", escopos));
    }
}

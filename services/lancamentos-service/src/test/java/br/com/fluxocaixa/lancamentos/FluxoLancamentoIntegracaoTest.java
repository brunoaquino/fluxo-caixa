package br.com.fluxocaixa.lancamentos;

import org.junit.jupiter.api.Test;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FluxoLancamentoIntegracaoTest {

    private static final UUID COMERCIANTE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres")
    );
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.8").asCompatibleSubstituteFor("localstack/localstack")
    ).withServices("sns", "sqs");

    static String topicArn;
    static String queueUrl;
    static SqsClient sqs;
    static final List<Message> mensagens = new ArrayList<>();

    static {
        POSTGRES.start();
        LOCALSTACK.start();
        var credenciais = StaticCredentialsProvider.create(AwsBasicCredentials.create(
                LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
        var regiao = Region.of(LOCALSTACK.getRegion());
        try (SnsClient sns = SnsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .credentialsProvider(credenciais)
                .region(regiao)
                .build()) {
            sqs = SqsClient.builder()
                    .endpointOverride(LOCALSTACK.getEndpoint())
                    .credentialsProvider(credenciais)
                    .region(regiao)
                    .build();
            topicArn = sns.createTopic(builder -> builder.name("fluxo-caixa-lancamentos-events")).topicArn();
            queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                    .queueName("consolidado-lancamentos-queue")
                    .build()).queueUrl();
            String queueArn = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl)
                            .attributeNames(QueueAttributeName.QUEUE_ARN)
                            .build())
                    .attributes()
                    .get(QueueAttributeName.QUEUE_ARN);
            sqs.setQueueAttributes(SetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributes(Map.of(QueueAttributeName.POLICY, """
                            {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"sqs:SendMessage","Resource":"%s"}]}
                            """.formatted(queueArn)))
                    .build());
            sns.subscribe(SubscribeRequest.builder()
                    .topicArn(topicArn)
                    .protocol("sqs")
                    .endpoint(queueArn)
                    .attributes(Map.of("RawMessageDelivery", "true"))
                    .build());
        }
    }

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("DB_URL", POSTGRES::getJdbcUrl);
        registro.add("DB_USERNAME", POSTGRES::getUsername);
        registro.add("DB_PASSWORD", POSTGRES::getPassword);
        registro.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registro.add("spring.datasource.username", POSTGRES::getUsername);
        registro.add("spring.datasource.password", POSTGRES::getPassword);
        registro.add("AWS_ENDPOINT_URL", () -> LOCALSTACK.getEndpoint().toString());
        registro.add("AWS_REGION", LOCALSTACK::getRegion);
        registro.add("AWS_ACCESS_KEY_ID", LOCALSTACK::getAccessKey);
        registro.add("AWS_SECRET_ACCESS_KEY", LOCALSTACK::getSecretKey);
        registro.add("LANCAMENTOS_TOPIC_ARN", () -> topicArn);
        registro.add("app.messaging.topic-arn", () -> topicArn);
        registro.add("JWT_ISSUER_URI", () -> "http://localhost/realms/fluxo-caixa");
        registro.add("JWT_JWK_SET_URI", () -> "http://127.0.0.1:9/jwks");
        registro.add("app.outbox.intervalo-ms", () -> "200");
        registro.add("app.outbox.lote", () -> "100");
        registro.add("app.outbox.backoff-maximo-segundos", () -> "60");
        registro.add("management.opentelemetry.enabled", () -> "false");
        registro.add("management.tracing.export.otlp.enabled", () -> "false");
        registro.add("management.otlp.metrics.export.enabled", () -> "false");
        registro.add("management.logging.export.otlp.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbc;

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

    private JsonNode aguardarEvento(String lancamentoId) {
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
        return jsonMapper.readTree(mensagem.body());
    }

    private static void drenarFila() {
        var resposta = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .messageAttributeNames("All")
                .build());
        for (Message mensagem : resposta.messages()) {
            mensagens.add(mensagem);
            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(mensagem.receiptHandle())
                    .build());
        }
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor token(String escopos) {
        return jwt().jwt(token -> token
                .claim("comerciante_id", COMERCIANTE.toString())
                .claim("scope", escopos));
    }
}

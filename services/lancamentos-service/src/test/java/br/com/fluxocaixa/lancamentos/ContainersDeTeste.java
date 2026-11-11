package br.com.fluxocaixa.lancamentos;

import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public final class ContainersDeTeste {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine").asCompatibleSubstituteFor("postgres")
    );
    public static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.8").asCompatibleSubstituteFor("localstack/localstack")
    ).withServices("sns", "sqs");

    public static String topicArn;
    public static String queueUrl;
    public static SqsClient sqs;

    static {
        POSTGRES.start();
        criarBanco("roteamento");
        criarBanco("roteamento_leitura");
        criarBanco("resiliencia");
        LOCALSTACK.start();
        var credenciais = StaticCredentialsProvider.create(AwsBasicCredentials.create(
                LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
        var regiao = Region.of(LOCALSTACK.getRegion());
        try (SnsClient sns = SnsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .credentialsProvider(credenciais)
                .region(regiao)
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build()) {
            sqs = SqsClient.builder()
                    .endpointOverride(LOCALSTACK.getEndpoint())
                    .credentialsProvider(credenciais)
                    .region(regiao)
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
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

    private ContainersDeTeste() {
    }

    public static String jdbcUrl(String banco) {
        String url = POSTGRES.getJdbcUrl();
        int barra = url.lastIndexOf('/');
        return url.substring(0, barra + 1) + banco;
    }

    private static void criarBanco(String nome) {
        try (Connection conexao = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement comando = conexao.createStatement()) {
            comando.execute("CREATE DATABASE " + nome);
        } catch (SQLException ex) {
            if (ex.getMessage() == null || !ex.getMessage().contains("already exists")) {
                throw new IllegalStateException("Não foi possível criar o banco " + nome, ex);
            }
        }
    }
}

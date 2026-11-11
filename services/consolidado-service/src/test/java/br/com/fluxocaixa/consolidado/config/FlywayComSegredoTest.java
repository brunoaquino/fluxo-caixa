package br.com.fluxocaixa.consolidado.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class FlywayComSegredoTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("consolidado")
            .withUsername("consolidado")
            .withPassword("consolidado")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer("localstack/localstack:4.7")
            .withServices("secretsmanager")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Test
    void flywayMigraPeloWrapperQuandoHaSegredo() {
        String chaveAnterior = System.setProperty("aws.accessKeyId", "test");
        String segredoAnterior = System.setProperty("aws.secretAccessKey", "test");
        String regiaoAnterior = System.setProperty("aws.region", "us-east-1");
        try (SecretsManagerClient segredos = SecretsManagerClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of("us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build()) {
            String arn = segredos.createSecret(pedido -> pedido
                    .name("consolidado-db")
                    .secretString("{\"username\":\"consolidado\",\"password\":\"consolidado\"}")).arn();
            String regiao = arn.split(":")[3];
            HikariConfig config = SeletorConexao.montar(new SeletorConexao.Pedido(
                    POSTGRES.getJdbcUrl(),
                    null,
                    null,
                    arn,
                    regiao,
                    LOCALSTACK.getEndpoint().toString(),
                    "consolidado-segredo",
                    false,
                    SeletorConexao.Pool.padrao()));
            try (HikariDataSource dataSource = new HikariDataSource(config)) {
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();
                Integer tabelas = new JdbcTemplate(dataSource).queryForObject(
                        "select count(*) from information_schema.tables where table_name = 'saldos_diarios'",
                        Integer.class);
                assertThat(tabelas).isEqualTo(1);
            }
        } finally {
            restaurar("aws.accessKeyId", chaveAnterior);
            restaurar("aws.secretAccessKey", segredoAnterior);
            restaurar("aws.region", regiaoAnterior);
        }
    }

    private static void restaurar(String chave, String anterior) {
        if (anterior == null) {
            System.clearProperty(chave);
        } else {
            System.setProperty(chave, anterior);
        }
    }
}

package br.com.fluxocaixa.consolidado.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@Import(JwtDecoderTesteConfig.class)
class RoteamentoLeituraEscritaTest {

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

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> comApplicationName(POSTGRES.getJdbcUrl(), "escrita"));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.datasource.reader-url", () -> comApplicationName(POSTGRES.getJdbcUrl(), "leitura"));
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("app.messaging.consumo-habilitado", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transacoes;

    @Test
    void leituraVaiParaReplicaEEscritaParaPrimario() {
        TransactionTemplate leitura = new TransactionTemplate(transacoes);
        leitura.setReadOnly(true);
        String nomeLeitura = leitura.execute(status ->
                jdbc.queryForObject("select current_setting('application_name')", String.class));

        TransactionTemplate escrita = new TransactionTemplate(transacoes);
        escrita.setReadOnly(false);
        String nomeEscrita = escrita.execute(status ->
                jdbc.queryForObject("select current_setting('application_name')", String.class));

        assertThat(nomeLeitura).isEqualTo("leitura");
        assertThat(nomeEscrita).isEqualTo("escrita");
    }

    private static String comApplicationName(String url, String nome) {
        String separador = url.contains("?") ? "&" : "?";
        return url + separador + "ApplicationName=" + nome;
    }
}

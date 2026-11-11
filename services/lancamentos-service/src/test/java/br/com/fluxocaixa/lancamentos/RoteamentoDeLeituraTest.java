package br.com.fluxocaixa.lancamentos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.outbox.intervalo-ms=3600000")
class RoteamentoDeLeituraTest {

    @DynamicPropertySource
    static void propriedades(DynamicPropertyRegistry registro) {
        registro.add("DB_URL", () -> ContainersDeTeste.jdbcUrl("roteamento"));
        registro.add("DB_READER_URL", () -> ContainersDeTeste.jdbcUrl("roteamento_leitura"));
        registro.add("DB_USERNAME", ContainersDeTeste.POSTGRES::getUsername);
        registro.add("DB_PASSWORD", ContainersDeTeste.POSTGRES::getPassword);
        registro.add("spring.datasource.url", () -> ContainersDeTeste.jdbcUrl("roteamento"));
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
    private DataSource dataSource;

    @Autowired
    private SondaDeBanco sonda;

    @Test
    void leituraVaiParaReplicaEEscritaParaPrimario() {
        assertThat(dataSource).isInstanceOf(LazyConnectionDataSourceProxy.class);
        assertThat(sonda.leitura()).isEqualTo("roteamento_leitura");
        assertThat(sonda.escrita()).isEqualTo("roteamento");
    }

    @TestConfiguration
    static class ConfiguracaoDaSonda {

        @Bean
        SondaDeBanco sondaDeBanco(JdbcTemplate jdbc) {
            return new SondaDeBanco(jdbc);
        }
    }

    static class SondaDeBanco {

        private final JdbcTemplate jdbc;

        SondaDeBanco(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional(readOnly = true)
        public String leitura() {
            return jdbc.queryForObject("select current_database()", String.class);
        }

        @Transactional
        public String escrita() {
            return jdbc.queryForObject("select current_database()", String.class);
        }
    }
}

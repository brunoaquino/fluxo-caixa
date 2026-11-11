package br.com.fluxocaixa.consolidado.config;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeletorConexaoTest {

    @Test
    void semSegredoUsaDriverPostgresqlECredenciais() {
        HikariConfig config = SeletorConexao.montar(pedido(null, "jdbc:postgresql://localhost:5432/consolidado", false));

        assertThat(config.getDriverClassName()).isEqualTo(SeletorConexao.DRIVER_POSTGRES);
        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/consolidado");
        assertThat(config.getUsername()).isEqualTo("consolidado");
        assertThat(config.getPassword()).isEqualTo("consolidado");
        assertThat(config.getDataSourceProperties()).doesNotContainKey("wrapperPlugins");
        assertThat(config.getMaxLifetime()).isEqualTo(900_000L);
        assertThat(config.getConnectionInitSql()).contains("statement_timeout");
        assertThat(config.isReadOnly()).isFalse();
    }

    @Test
    void comSegredoUsaWrapperFailoverESecretsManager() {
        String arn = "arn:aws:secretsmanager:us-east-1:123456789012:secret:consolidado-AbCdEf";
        HikariConfig config = SeletorConexao.montar(new SeletorConexao.Pedido(
                "jdbc:postgresql://db.exemplo.us-east-1.rds.amazonaws.com:5432/consolidado?ApplicationName=leitura",
                "ignorado",
                "ignorado",
                arn,
                "us-east-1",
                "http://localhost:4566",
                "consolidado-leitura",
                true,
                SeletorConexao.Pool.padrao()));

        assertThat(config.getDriverClassName()).isEqualTo(SeletorConexao.DRIVER_WRAPPER);
        assertThat(config.getJdbcUrl()).isEqualTo(
                "jdbc:aws-wrapper:postgresql://db.exemplo.us-east-1.rds.amazonaws.com:5432/consolidado?ApplicationName=leitura");
        assertThat(config.getUsername()).isNull();
        assertThat(config.getDataSourceProperties().getProperty("wrapperPlugins")).isEqualTo(SeletorConexao.PLUGINS_INSTANCIA);
        assertThat(config.getDataSourceProperties().getProperty("secretsManagerSecretId")).isEqualTo(arn);
        assertThat(config.getDataSourceProperties().getProperty("secretsManagerRegion")).isEqualTo("us-east-1");
        assertThat(config.getDataSourceProperties().getProperty("secretsManagerEndpoint")).isEqualTo("http://localhost:4566");
        assertThat(config.isReadOnly()).isTrue();
    }

    @Test
    void segredoEmBrancoCaiNoDriverPadrao() {
        HikariConfig config = SeletorConexao.montar(pedido("   ", "jdbc:postgresql://localhost:5432/consolidado", false));

        assertThat(config.getDriverClassName()).isEqualTo(SeletorConexao.DRIVER_POSTGRES);
        assertThat(config.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/consolidado");
        assertThat(config.getUsername()).isEqualTo("consolidado");
        assertThat(config.getDataSourceProperties()).doesNotContainKey("wrapperPlugins");
    }

    @Test
    void rejeitaMaxLifetimeAcimaDaRotacao() {
        SeletorConexao.Pool longo = new SeletorConexao.Pool(10, 2, 3_000, 1_000, 30_000, 86_400_000L, 300_000, "SET statement_timeout = '3s'");
        assertThatThrownBy(() -> SeletorConexao.montar(new SeletorConexao.Pedido(
                "jdbc:postgresql://localhost/consolidado",
                "consolidado",
                "consolidado",
                null,
                "us-east-1",
                null,
                "consolidado-pool",
                false,
                longo))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aceitaOsDoisFormatosSemDuplicarOPrefixo() {
        String postgres = "jdbc:postgresql://db.exemplo.us-east-1.rds.amazonaws.com:5432/consolidado";
        String wrapper = "jdbc:aws-wrapper:postgresql://db.exemplo.us-east-1.rds.amazonaws.com:5432/consolidado";
        String dobrado = "jdbc:aws-wrapper:" + wrapper.substring("jdbc:".length());

        assertThat(SeletorConexao.normalizar(postgres, true)).isEqualTo(wrapper);
        assertThat(SeletorConexao.normalizar(wrapper, true)).isEqualTo(wrapper);
        assertThat(SeletorConexao.normalizar(dobrado, true)).isEqualTo(wrapper);
        assertThat(SeletorConexao.normalizar(wrapper, true)).doesNotContain("aws-wrapper:aws-wrapper");

        HikariConfig comPrefixo = SeletorConexao.montar(pedido("arn:secret", wrapper, false));
        assertThat(comPrefixo.getJdbcUrl()).isEqualTo(wrapper);
        assertThat(comPrefixo.getDataSourceProperties().getProperty("wrapperPlugins"))
                .isEqualTo(SeletorConexao.PLUGINS_INSTANCIA);

        HikariConfig semSegredo = SeletorConexao.montar(pedido(null, wrapper, false));
        assertThat(semSegredo.getJdbcUrl()).isEqualTo(postgres);
        assertThat(semSegredo.getDriverClassName()).isEqualTo(SeletorConexao.DRIVER_POSTGRES);
    }

    @Test
    void proxyNaoUsaPluginDeFailover() {
        String proxy = "jdbc:postgresql://app.proxy-abc123.us-east-1.rds.amazonaws.com:5432/consolidado";
        String reader = "jdbc:aws-wrapper:postgresql://app-ro.endpoint.proxy-abc123.us-east-1.rds.amazonaws.com:5432/consolidado";

        HikariConfig escrita = SeletorConexao.montar(pedido("arn:secret", proxy, false));
        HikariConfig leitura = SeletorConexao.montar(pedido("arn:secret", reader, true));

        assertThat(escrita.getJdbcUrl()).isEqualTo("jdbc:aws-wrapper:" + proxy.substring("jdbc:".length()));
        assertThat(escrita.getDataSourceProperties().getProperty("wrapperPlugins"))
                .isEqualTo(SeletorConexao.PLUGINS_PROXY);
        assertThat(leitura.getJdbcUrl()).doesNotContain("aws-wrapper:aws-wrapper");
        assertThat(leitura.getDataSourceProperties().getProperty("wrapperPlugins"))
                .isEqualTo(SeletorConexao.PLUGINS_PROXY);
    }

    @Test
    void urlQueNaoEPostgresNaoViraWrapper() {
        assertThatThrownBy(() -> SeletorConexao.normalizar("jdbc:h2:mem:teste", true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SeletorConexao.Pedido pedido(String segredo, String url, boolean leitura) {
        return new SeletorConexao.Pedido(
                url,
                "consolidado",
                "consolidado",
                segredo,
                "us-east-1",
                null,
                "consolidado-pool",
                leitura,
                SeletorConexao.Pool.padrao());
    }
}

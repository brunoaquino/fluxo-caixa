package br.com.fluxocaixa.lancamentos.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SelecaoDeConexaoTest {

    @Test
    void perfilAwsComSegredoUsaWrapperEPlugins() {
        SelecaoDeConexao.Escolha escolha = SelecaoDeConexao.escolher(
                "jdbc:postgresql://db.interno:5432/lancamentos",
                "ignorado",
                "ignorado",
                "arn:aws:secretsmanager:us-east-1:123:secret:rds",
                "us-east-1",
                true
        );

        assertThat(escolha.usaSegredo()).isTrue();
        assertThat(escolha.driver()).isEqualTo(SelecaoDeConexao.DRIVER_WRAPPER);
        assertThat(escolha.url()).isEqualTo("jdbc:aws-wrapper:postgresql://db.interno:5432/lancamentos");
        assertThat(escolha.plugins()).isEqualTo(SelecaoDeConexao.PLUGINS_INSTANCIA);
        assertThat(escolha.username()).isNull();
        assertThat(escolha.secretArn()).contains("rds");

        var pool = ConfiguracaoDePool.montar(escolha, "lancamentos-escrita", 10, ConfiguracaoDePool.MAX_LIFETIME_PADRAO_MS);
        assertThat(pool.getDriverClassName()).isEqualTo(SelecaoDeConexao.DRIVER_WRAPPER);
        assertThat(pool.getMaxLifetime()).isEqualTo(900_000L);
        assertThat(pool.getMaxLifetime()).isLessThan(java.time.Duration.ofDays(30).toMillis());
        assertThat(pool.getDataSourceProperties().getProperty("wrapperPlugins")).isEqualTo(SelecaoDeConexao.PLUGINS_INSTANCIA);
        assertThat(pool.getDataSourceProperties().getProperty("secretsManagerSecretId")).contains("rds");
        assertThat(pool.getDataSourceProperties().getProperty("connectTimeout")).isEqualTo("3000");
        assertThat(pool.getDataSourceProperties().getProperty("socketTimeout")).isEqualTo("5000");
        assertThat(pool.getConnectionInitSql()).contains("statement_timeout");
    }

    @Test
    void semSegredoOuForaDoPerfilAwsUsaPostgres() {
        SelecaoDeConexao.Escolha semSegredo = SelecaoDeConexao.escolher(
                "jdbc:postgresql://localhost:5432/lancamentos", "lancamentos", "lancamentos", null, "us-east-1", true);
        SelecaoDeConexao.Escolha semPerfil = SelecaoDeConexao.escolher(
                "jdbc:postgresql://localhost:5432/lancamentos", "lancamentos", "lancamentos", "arn:secret", "us-east-1", false);
        SelecaoDeConexao.Escolha segredoEmBranco = SelecaoDeConexao.escolher(
                "jdbc:postgresql://localhost:5432/lancamentos", "lancamentos", "lancamentos", "  ", "us-east-1", true);

        assertThat(semSegredo.driver()).isEqualTo(SelecaoDeConexao.DRIVER_POSTGRES);
        assertThat(semPerfil.driver()).isEqualTo(SelecaoDeConexao.DRIVER_POSTGRES);
        assertThat(segredoEmBranco.driver()).isEqualTo(SelecaoDeConexao.DRIVER_POSTGRES);
        assertThat(semSegredo.username()).isEqualTo("lancamentos");
        assertThat(semSegredo.usaSegredo()).isFalse();

        var pool = ConfiguracaoDePool.montar(semSegredo, "lancamentos-escrita", 10, 900_000L);
        assertThat(pool.getJdbcUrl()).startsWith("jdbc:postgresql:");
        assertThat(pool.getUsername()).isEqualTo("lancamentos");
        assertThat(pool.getDataSourceProperties()).doesNotContainKey("wrapperPlugins");
        assertThat(pool.getDataSourceProperties().getProperty("connectTimeout")).isEqualTo("3");
        assertThat(pool.getDataSourceProperties().getProperty("socketTimeout")).isEqualTo("5");
    }

    @Test
    void aceitaOsDoisFormatosSemDuplicarOPrefixo() {
        String postgres = "jdbc:postgresql://db.interno:5432/lancamentos";
        String wrapper = "jdbc:aws-wrapper:postgresql://db.interno:5432/lancamentos";
        String dobrado = "jdbc:aws-wrapper:aws-wrapper:postgresql://db.interno:5432/lancamentos";

        assertThat(SelecaoDeConexao.normalizar(postgres, true)).isEqualTo(wrapper);
        assertThat(SelecaoDeConexao.normalizar(wrapper, true)).isEqualTo(wrapper);
        assertThat(SelecaoDeConexao.normalizar(dobrado, true)).isEqualTo(wrapper);
        assertThat(SelecaoDeConexao.normalizar(wrapper, false)).isEqualTo(postgres);
        assertThat(SelecaoDeConexao.normalizar(wrapper, true)).doesNotContain("aws-wrapper:aws-wrapper");

        SelecaoDeConexao.Escolha jaPrefixada = SelecaoDeConexao.escolher(
                wrapper, "ignorado", "ignorado", "arn:secret", "us-east-1", true);
        assertThat(jaPrefixada.url()).isEqualTo(wrapper);

        SelecaoDeConexao.Escolha semSegredo = SelecaoDeConexao.escolher(
                wrapper, "lancamentos", "lancamentos", null, "us-east-1", true);
        assertThat(semSegredo.url()).isEqualTo(postgres);
        assertThat(semSegredo.driver()).isEqualTo(SelecaoDeConexao.DRIVER_POSTGRES);
    }

    @Test
    void proxyNaoUsaPluginDeFailover() {
        String proxy = "jdbc:postgresql://app.proxy-abc123.us-east-1.rds.amazonaws.com:5432/lancamentos";
        String reader = "jdbc:aws-wrapper:postgresql://app-ro.endpoint.proxy-abc123.us-east-1.rds.amazonaws.com:5432/lancamentos";

        SelecaoDeConexao.Escolha escrita = SelecaoDeConexao.escolher(
                proxy, "ignorado", "ignorado", "arn:secret", "us-east-1", true);
        SelecaoDeConexao.Escolha leitura = SelecaoDeConexao.escolher(
                reader, "ignorado", "ignorado", "arn:secret", null, true);

        assertThat(escrita.plugins()).isEqualTo(SelecaoDeConexao.PLUGINS_PROXY);
        assertThat(escrita.url()).isEqualTo("jdbc:aws-wrapper:" + proxy.substring("jdbc:".length()));
        assertThat(leitura.plugins()).isEqualTo(SelecaoDeConexao.PLUGINS_PROXY);
        assertThat(leitura.url()).doesNotContain("aws-wrapper:aws-wrapper");
        assertThat(leitura.region()).isEqualTo(SelecaoDeConexao.REGIAO_PADRAO);
    }

    @Test
    void urlQueNaoEPostgresNaoViraWrapper() {
        assertThatThrownBy(() -> SelecaoDeConexao.normalizar("jdbc:h2:mem:teste", true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

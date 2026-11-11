package br.com.fluxocaixa.consolidado.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class ConfiguracaoDataSource {

    @Bean(name = "dataSourceEscrita")
    @FlywayDataSource
    DataSource dataSourceEscrita(Environment env, PropriedadesAplicacao propriedades) {
        return new HikariDataSource(SeletorConexao.montar(pedido(env, propriedades, false)));
    }

    @Bean(name = "dataSourceLeitura")
    @Conditional(LeituraConfigurada.class)
    DataSource dataSourceLeitura(Environment env, PropriedadesAplicacao propriedades) {
        return new HikariDataSource(SeletorConexao.montar(pedido(env, propriedades, true)));
    }

    @Bean
    @Primary
    DataSource dataSource(
            @Qualifier("dataSourceEscrita") DataSource escrita,
            @Qualifier("dataSourceLeitura") ObjectProvider<DataSource> leitura) {
        DataSource replica = leitura.getIfAvailable();
        if (replica == null) {
            return escrita;
        }
        RoteadorLeituraEscrita roteador = new RoteadorLeituraEscrita();
        Map<Object, Object> alvos = new HashMap<>();
        alvos.put(RoteadorLeituraEscrita.ESCRITA, escrita);
        alvos.put(RoteadorLeituraEscrita.LEITURA, replica);
        roteador.setTargetDataSources(alvos);
        roteador.setDefaultTargetDataSource(escrita);
        roteador.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(roteador);
    }

    private static SeletorConexao.Pedido pedido(Environment env, PropriedadesAplicacao propriedades, boolean leitura) {
        String url = leitura ? propriedades.datasource().readerUrl() : env.getProperty("spring.datasource.url");
        String pool = leitura
                ? "consolidado-leitura"
                : env.getProperty("spring.datasource.hikari.pool-name", "consolidado-pool");
        return new SeletorConexao.Pedido(
                url,
                env.getProperty("spring.datasource.username"),
                env.getProperty("spring.datasource.password"),
                propriedades.datasource().secretArn(),
                propriedades.aws().region(),
                propriedades.aws().endpointUrl(),
                pool,
                leitura,
                pool(env));
    }

    private static SeletorConexao.Pool pool(Environment env) {
        return new SeletorConexao.Pool(
                env.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, 10),
                env.getProperty("spring.datasource.hikari.minimum-idle", Integer.class, 2),
                env.getProperty("spring.datasource.hikari.connection-timeout", Long.class, 3_000L),
                env.getProperty("spring.datasource.hikari.validation-timeout", Long.class, 1_000L),
                env.getProperty("spring.datasource.hikari.idle-timeout", Long.class, 30_000L),
                env.getProperty("spring.datasource.hikari.max-lifetime", Long.class, 900_000L),
                env.getProperty("spring.datasource.hikari.keepalive-time", Long.class, 300_000L),
                env.getProperty("spring.datasource.hikari.connection-init-sql", "SET statement_timeout = '3s'"));
    }
}

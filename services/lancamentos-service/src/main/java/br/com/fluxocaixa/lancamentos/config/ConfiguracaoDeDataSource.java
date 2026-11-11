package br.com.fluxocaixa.lancamentos.config;

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
import java.util.Map;

/**
 * O Flyway recebe sempre o pool de escrita. Com {@code DB_READER_URL}, a
 * aplicação enxerga um roteador preguiçoso; sem a variável, o mesmo pool.
 */
@Configuration(proxyBeanMethods = false)
public class ConfiguracaoDeDataSource {

    @Bean(name = "dataSourceDeEscrita", destroyMethod = "close")
    @FlywayDataSource
    HikariDataSource dataSourceDeEscrita(Environment ambiente) {
        return pool(ambiente, ambiente.getRequiredProperty("spring.datasource.url"), "lancamentos-escrita");
    }

    @Bean(name = "dataSourceDeLeitura", destroyMethod = "close")
    @Conditional(UrlDeLeituraPresente.class)
    HikariDataSource dataSourceDeLeitura(Environment ambiente) {
        return pool(ambiente, ambiente.getRequiredProperty("DB_READER_URL"), "lancamentos-leitura");
    }

    @Bean
    @Primary
    DataSource dataSource(
            @Qualifier("dataSourceDeEscrita") HikariDataSource escrita,
            @Qualifier("dataSourceDeLeitura") ObjectProvider<HikariDataSource> leitura,
            MarcadorDeLeitura marcador
    ) {
        HikariDataSource replica = leitura.getIfAvailable();
        if (replica == null) {
            return escrita;
        }
        RoteadorLeituraEscrita roteador = new RoteadorLeituraEscrita(marcador);
        roteador.setDefaultTargetDataSource(escrita);
        roteador.setTargetDataSources(Map.of(
                RoteadorLeituraEscrita.ESCRITA, escrita,
                RoteadorLeituraEscrita.LEITURA, replica
        ));
        roteador.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(roteador);
    }

    private static HikariDataSource pool(Environment ambiente, String url, String nome) {
        SelecaoDeConexao.Escolha escolha = SelecaoDeConexao.escolher(
                url,
                ambiente.getProperty("spring.datasource.username"),
                ambiente.getProperty("spring.datasource.password"),
                ambiente.getProperty("DB_SECRET_ARN"),
                ambiente.getProperty("AWS_REGION", SelecaoDeConexao.REGIAO_PADRAO),
                perfilAws(ambiente)
        );
        int tamanho = ambiente.getProperty("spring.datasource.hikari.maximum-pool-size", Integer.class, 10);
        long vida = ambiente.getProperty("spring.datasource.hikari.max-lifetime", Long.class, ConfiguracaoDePool.MAX_LIFETIME_PADRAO_MS);
        return new HikariDataSource(ConfiguracaoDePool.montar(escolha, nome, tamanho, vida));
    }

    private static boolean perfilAws(Environment ambiente) {
        for (String perfil : ambiente.getActiveProfiles()) {
            if ("aws".equals(perfil)) {
                return true;
            }
        }
        return false;
    }
}

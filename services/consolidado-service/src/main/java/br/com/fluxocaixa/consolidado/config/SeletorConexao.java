package br.com.fluxocaixa.consolidado.config;

import com.zaxxer.hikari.HikariConfig;

import java.time.Duration;
import java.util.Locale;

/**
 * Escolhe o driver PostgreSQL comum ou o AWS JDBC Wrapper com Secrets Manager.
 * O pool só é aberto por quem recebe o {@link HikariConfig}; este tipo não conecta.
 */
public final class SeletorConexao {

    public static final String DRIVER_POSTGRES = "org.postgresql.Driver";
    public static final String DRIVER_WRAPPER = "software.amazon.jdbc.Driver";
    /** Instância ou cluster. O failover fala com o endpoint da instância, não com o proxy. */
    public static final String PLUGINS_INSTANCIA = "failover,awsSecretsManager";
    /** RDS Proxy já faz o failover. O plugin furaria o proxy e iria na instância. */
    public static final String PLUGINS_PROXY = "awsSecretsManager";
    static final long MAX_LIFETIME_LIMITE_MS = Duration.ofHours(12).toMillis();

    private SeletorConexao() {
    }

    public static HikariConfig montar(Pedido pedido) {
        if (pedido.url() == null || pedido.url().isBlank()) {
            throw new IllegalArgumentException("DB_URL é obrigatória");
        }
        validarPool(pedido.pool());
        HikariConfig config = new HikariConfig();
        config.setPoolName(pedido.poolName());
        config.setMaximumPoolSize(pedido.pool().maximumPoolSize());
        config.setMinimumIdle(pedido.pool().minimumIdle());
        config.setConnectionTimeout(pedido.pool().connectionTimeoutMs());
        config.setValidationTimeout(pedido.pool().validationTimeoutMs());
        config.setIdleTimeout(pedido.pool().idleTimeoutMs());
        config.setMaxLifetime(pedido.pool().maxLifetimeMs());
        config.setKeepaliveTime(pedido.pool().keepaliveTimeMs());
        config.setConnectionInitSql(pedido.pool().connectionInitSql());
        config.setReadOnly(pedido.somenteLeitura());
        config.addDataSourceProperty("connectTimeout", "3");
        config.addDataSourceProperty("socketTimeout", "5");
        if (pedido.usaSegredo()) {
            String urlWrapper = normalizar(pedido.url(), true);
            config.setJdbcUrl(urlWrapper);
            config.setDriverClassName(DRIVER_WRAPPER);
            config.addDataSourceProperty("wrapperPlugins", pluginsPara(urlWrapper));
            config.addDataSourceProperty("secretsManagerSecretId", pedido.secretArn().trim());
            if (pedido.region() != null && !pedido.region().isBlank()) {
                config.addDataSourceProperty("secretsManagerRegion", pedido.region().trim());
            }
            if (pedido.endpointAws() != null && !pedido.endpointAws().isBlank()) {
                config.addDataSourceProperty("secretsManagerEndpoint", pedido.endpointAws().trim());
            }
            return config;
        }
        if (pedido.username() == null || pedido.username().isBlank()) {
            throw new IllegalArgumentException("DB_USERNAME é obrigatório quando DB_SECRET_ARN não está definido");
        }
        config.setJdbcUrl(normalizar(pedido.url(), false));
        config.setUsername(pedido.username());
        config.setPassword(pedido.password() == null ? "" : pedido.password());
        config.setDriverClassName(DRIVER_POSTGRES);
        return config;
    }

    /**
     * Aceita {@code jdbc:postgresql://} e {@code jdbc:aws-wrapper:postgresql://}.
     * Prefixos repetidos do wrapper são removidos antes de aplicar um só.
     */
    static String normalizar(String url, boolean wrapper) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("DB_URL é obrigatória");
        }
        String base = url.trim();
        while (base.startsWith("jdbc:aws-wrapper:")) {
            base = "jdbc:" + base.substring("jdbc:aws-wrapper:".length());
        }
        if (!base.startsWith("jdbc:postgresql:")) {
            throw new IllegalArgumentException(
                    "DB_URL deve usar jdbc:postgresql:// ou jdbc:aws-wrapper:postgresql://");
        }
        if (!wrapper) {
            return base;
        }
        return "jdbc:aws-wrapper:" + base.substring("jdbc:".length());
    }

    static String pluginsPara(String url) {
        return endpointDeProxy(url) ? PLUGINS_PROXY : PLUGINS_INSTANCIA;
    }

    static boolean endpointDeProxy(String url) {
        return hostDe(url).contains(".proxy-");
    }

    static String hostDe(String url) {
        int esquema = url.indexOf("://");
        if (esquema < 0) {
            return "";
        }
        String resto = url.substring(esquema + 3);
        int fim = resto.length();
        int barra = resto.indexOf('/');
        int interrogacao = resto.indexOf('?');
        if (barra >= 0) {
            fim = Math.min(fim, barra);
        }
        if (interrogacao >= 0) {
            fim = Math.min(fim, interrogacao);
        }
        String autoridade = resto.substring(0, fim);
        int arroba = autoridade.lastIndexOf('@');
        if (arroba >= 0) {
            autoridade = autoridade.substring(arroba + 1);
        }
        if (autoridade.startsWith("[")) {
            int fecha = autoridade.indexOf(']');
            return fecha > 0 ? autoridade.substring(1, fecha) : autoridade;
        }
        int doisPontos = autoridade.lastIndexOf(':');
        if (doisPontos >= 0) {
            autoridade = autoridade.substring(0, doisPontos);
        }
        return autoridade.toLowerCase(Locale.ROOT);
    }

    private static void validarPool(Pool pool) {
        if (pool.maxLifetimeMs() < 30_000 || pool.maxLifetimeMs() > MAX_LIFETIME_LIMITE_MS) {
            throw new IllegalArgumentException(
                    "Hikari max-lifetime deve ficar entre 30s e 12h, abaixo da rotação do segredo");
        }
        if (pool.keepaliveTimeMs() >= pool.maxLifetimeMs()) {
            throw new IllegalArgumentException("keepalive-time deve ser menor que max-lifetime");
        }
    }

    public record Pedido(
            String url,
            String username,
            String password,
            String secretArn,
            String region,
            String endpointAws,
            String poolName,
            boolean somenteLeitura,
            Pool pool) {

        public boolean usaSegredo() {
            return secretArn != null && !secretArn.isBlank();
        }
    }

    public record Pool(
            int maximumPoolSize,
            int minimumIdle,
            long connectionTimeoutMs,
            long validationTimeoutMs,
            long idleTimeoutMs,
            long maxLifetimeMs,
            long keepaliveTimeMs,
            String connectionInitSql) {

        public Pool {
            if (connectionInitSql == null || connectionInitSql.isBlank()) {
                connectionInitSql = "SET statement_timeout = '3s'";
            }
        }

        public static Pool padrao() {
            return new Pool(10, 2, 3_000, 1_000, 30_000, 900_000, 300_000, "SET statement_timeout = '3s'");
        }
    }
}

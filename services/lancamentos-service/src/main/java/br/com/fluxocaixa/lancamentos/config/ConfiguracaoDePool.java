package br.com.fluxocaixa.lancamentos.config;

import com.zaxxer.hikari.HikariConfig;

/**
 * Pool curto o bastante para pegar senha nova depois da rotação (30 dias em
 * prod) e para devolver conexão ao RDS antes do {@code statement_timeout}.
 */
public final class ConfiguracaoDePool {

    static final long MAX_LIFETIME_PADRAO_MS = 900_000L;
    static final String INIT_SQL = "SET statement_timeout = '30s'";

    private ConfiguracaoDePool() {
    }

    public static HikariConfig montar(SelecaoDeConexao.Escolha escolha, String nome, int maximumPoolSize, long maxLifetimeMs) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(nome);
        config.setJdbcUrl(escolha.url());
        config.setDriverClassName(escolha.driver());
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(Math.min(2, maximumPoolSize));
        config.setMaxLifetime(maxLifetimeMs);
        config.setKeepaliveTime(120_000L);
        config.setIdleTimeout(600_000L);
        config.setConnectionTimeout(5_000L);
        config.setConnectionInitSql(INIT_SQL);
        if (!escolha.usaSegredo()) {
            config.setUsername(escolha.username());
            config.setPassword(escolha.password());
        }
        if (escolha.usaSegredo()) {
            config.addDataSourceProperty("wrapperPlugins", escolha.plugins());
            config.addDataSourceProperty("secretsManagerSecretId", escolha.secretArn());
            config.addDataSourceProperty("secretsManagerRegion", escolha.region());
        }
        return config;
    }
}

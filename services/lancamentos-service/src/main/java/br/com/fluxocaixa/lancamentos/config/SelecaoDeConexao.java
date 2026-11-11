package br.com.fluxocaixa.lancamentos.config;

import java.util.Locale;

/**
 * Escolhe o driver JDBC. O wrapper da AWS só entra no perfil {@code aws}
 * quando {@code DB_SECRET_ARN} está preenchido. Nos demais casos o Flyway e o
 * pool usam o driver PostgreSQL com usuário e senha.
 *
 * <p>{@code DB_URL} aceita {@code jdbc:postgresql://} e
 * {@code jdbc:aws-wrapper:postgresql://}. O prefixo do wrapper não é duplicado.
 * Contra o endpoint do RDS Proxy (hostname com {@code .proxy-}) o plugin
 * {@code failover} fica de fora: ele descobriria a instância e furaria o proxy.
 */
public final class SelecaoDeConexao {

    public static final String DRIVER_POSTGRES = "org.postgresql.Driver";
    public static final String DRIVER_WRAPPER = "software.amazon.jdbc.Driver";
    public static final String PLUGINS_INSTANCIA = "failover,awsSecretsManager";
    public static final String PLUGINS_PROXY = "awsSecretsManager";
    public static final String REGIAO_PADRAO = "us-east-1";

    private SelecaoDeConexao() {
    }

    public record Escolha(
            String url,
            String driver,
            String username,
            String password,
            String plugins,
            String secretArn,
            String region
    ) {
        public boolean usaSegredo() {
            return plugins != null;
        }
    }

    public static Escolha escolher(
            String url,
            String username,
            String password,
            String secretArn,
            String region,
            boolean perfilAws
    ) {
        if (perfilAws && secretArn != null && !secretArn.isBlank()) {
            String urlWrapper = normalizar(url, true);
            return new Escolha(
                    urlWrapper,
                    DRIVER_WRAPPER,
                    null,
                    null,
                    pluginsPara(urlWrapper),
                    secretArn.trim(),
                    region == null || region.isBlank() ? REGIAO_PADRAO : region.trim()
            );
        }
        return new Escolha(normalizar(url, false), DRIVER_POSTGRES, username, password, null, null, null);
    }

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
}

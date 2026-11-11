package br.com.fluxocaixa.consolidado.observabilidade;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC = "correlationId";

    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private CorrelationId() {
    }

    public static String resolver(String informado) {
        if (informado == null) {
            return UUID.randomUUID().toString();
        }
        String candidato = informado.trim();
        if (!UUID_CANONICO.matcher(candidato).matches()) {
            return UUID.randomUUID().toString();
        }
        return candidato.toLowerCase();
    }
}

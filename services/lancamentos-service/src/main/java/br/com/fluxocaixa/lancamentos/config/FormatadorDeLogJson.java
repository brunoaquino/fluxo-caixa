package br.com.fluxocaixa.lancamentos.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * JSON com os campos de rastreio exigidos pelo contrato: traceId, spanId,
 * correlationId e, quando a operação tem, comercianteId, lancamentoId e eventId.
 */
public class FormatadorDeLogJson implements StructuredLogFormatter<ILoggingEvent> {

    private static final DateTimeFormatter INSTANTE = DateTimeFormatter.ISO_INSTANT;

    private final JsonMapper json = JsonMapper.builder().build();

    @Override
    public String format(ILoggingEvent event) {
        ObjectNode no = json.createObjectNode();
        Instant quando = Instant.ofEpochMilli(event.getTimeStamp());
        no.put("@timestamp", INSTANTE.format(quando));
        no.put("level", event.getLevel().toString());
        no.put("logger", event.getLoggerName());
        no.put("message", sanitizar(event.getFormattedMessage()));
        no.put("service", "lancamentos-service");
        Map<String, String> mdc = event.getMDCPropertyMap();
        copiar(no, mdc, "traceId");
        copiar(no, mdc, "spanId");
        copiar(no, mdc, "correlationId");
        copiar(no, mdc, "comercianteId");
        copiar(no, mdc, "lancamentoId");
        copiar(no, mdc, "eventId");
        IThrowableProxy falha = event.getThrowableProxy();
        if (falha != null) {
            no.put("error", falha.getClassName());
            if (falha.getMessage() != null) {
                no.put("errorMessage", sanitizar(falha.getMessage()));
            }
        }
        return json.writeValueAsString(no) + System.lineSeparator();
    }

    private static void copiar(ObjectNode no, Map<String, String> mdc, String chave) {
        if (mdc == null) {
            return;
        }
        String valor = mdc.get(chave);
        if (valor != null && !valor.isBlank()) {
            no.put(chave, valor);
        }
    }

    static String sanitizar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto
                .replaceAll("(?i)bearer\\s+[\\w\\-._~+/=]+", "Bearer [redigido]")
                .replaceAll("(?i)(password|senha|secret|token)\\s*[:=]\\s*\\S+", "$1=[redigido]");
    }
}

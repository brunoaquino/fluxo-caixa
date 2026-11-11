package br.com.fluxocaixa.consolidado.observabilidade;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Abre um span CONSUMER filho do produtor e preenche o MDC na thread da mensagem.
 * Virtual threads não herdam o MDC do poller; a limpeza acontece na mesma thread,
 * no finally, para o próximo uso da thread não reaproveitar o contexto.
 */
@Component
public class RastreioMensagem {

    private static final Propagator.Getter<Map<String, String>> GETTER = (carrier, key) -> {
        if (carrier == null || key == null) {
            return null;
        }
        String direto = carrier.get(key);
        if (direto != null) {
            return direto;
        }
        for (Map.Entry<String, String> entrada : carrier.entrySet()) {
            if (entrada.getKey() != null && entrada.getKey().equalsIgnoreCase(key)) {
                return entrada.getValue();
            }
        }
        return null;
    };

    private final Tracer tracer;
    private final Propagator propagator;

    public RastreioMensagem(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public void executar(Map<String, String> atributos, Runnable acao) {
        Map<String, String> carrier = atributos == null ? Map.of() : atributos;
        String correlationId = CorrelationId.resolver(GETTER.get(carrier, "correlationId"));
        Span span = propagator.extract(carrier, GETTER)
                .name("lancamento process")
                .kind(Span.Kind.CONSUMER)
                .tag("messaging.system", "aws_sqs")
                .tag("messaging.operation", "process")
                .tag("messaging.destination.kind", "queue")
                .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            span.tag("correlation.id", correlationId);
            MDC.put("traceId", span.context().traceId());
            MDC.put("spanId", span.context().spanId());
            MDC.put(CorrelationId.MDC, correlationId);
            try {
                acao.run();
            } catch (RuntimeException ex) {
                span.error(ex);
                throw ex;
            }
        } finally {
            span.end();
            limparMdc();
        }
    }

    public static void limparMdc() {
        MDC.remove("traceId");
        MDC.remove("spanId");
        MDC.remove(CorrelationId.MDC);
        MDC.remove("eventId");
        MDC.remove("comercianteId");
    }
}

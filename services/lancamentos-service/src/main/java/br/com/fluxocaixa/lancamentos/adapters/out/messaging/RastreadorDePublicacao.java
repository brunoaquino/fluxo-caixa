package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class RastreadorDePublicacao {

    private final Tracer tracer;
    private final Propagator propagator;

    public RastreadorDePublicacao(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public Escopo abrir(EventoRastreavel evento) {
        Map<String, String> entrada = new LinkedHashMap<>();
        if (preenchido(evento.traceParent())) {
            entrada.put("traceparent", evento.traceParent());
        }
        if (preenchido(evento.traceState())) {
            entrada.put("tracestate", evento.traceState());
        }
        Span span = propagator.extract(entrada, (carrier, chave) -> carrier.get(chave))
                .name("publicar-lancamento")
                .kind(Span.Kind.PRODUCER)
                .start();
        Tracer.SpanInScope escopo = tracer.withSpan(span);
        Map<String, String> saida = new LinkedHashMap<>();
        propagator.inject(span.context(), saida, (carrier, chave, valor) -> carrier.put(chave, valor));
        colocar("traceId", span.context().traceId());
        colocar("spanId", span.context().spanId());
        colocar("correlationId", evento.correlationId());
        colocar("lancamentoId", evento.aggregateId().toString());
        colocar("eventId", evento.id().toString());
        colocar("comercianteId", evento.comercianteId());
        return new Escopo(span, escopo, new AtributosDeRastreio(
                saida.get("traceparent"),
                saida.get("tracestate"),
                evento.correlationId()
        ));
    }

    private static void colocar(String chave, String valor) {
        if (preenchido(valor)) {
            MDC.put(chave, valor);
        }
    }

    private static boolean preenchido(String valor) {
        return valor != null && !valor.isBlank();
    }

    public static final class Escopo implements AutoCloseable {

        private final Span span;
        private final Tracer.SpanInScope escopo;
        private final AtributosDeRastreio atributos;

        private Escopo(Span span, Tracer.SpanInScope escopo, AtributosDeRastreio atributos) {
            this.span = span;
            this.escopo = escopo;
            this.atributos = atributos;
        }

        public AtributosDeRastreio atributos() {
            return atributos;
        }

        public void erro(Throwable falha) {
            span.error(falha);
        }

        @Override
        public void close() {
            escopo.close();
            span.end();
            MDC.remove("traceId");
            MDC.remove("spanId");
            MDC.remove("correlationId");
            MDC.remove("lancamentoId");
            MDC.remove("eventId");
            MDC.remove("comercianteId");
        }
    }

    public record EventoRastreavel(
            UUID id,
            UUID aggregateId,
            String traceParent,
            String traceState,
            String correlationId,
            String comercianteId
    ) {
    }
}

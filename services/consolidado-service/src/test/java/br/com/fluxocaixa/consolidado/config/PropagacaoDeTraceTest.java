package br.com.fluxocaixa.consolidado.config;

import io.micrometer.observation.Observation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PropagacaoDeTraceTest {

    private static final TextMapGetter<Map<String, String>> LEITOR = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier == null ? null : carrier.get(key);
        }
    };

    @Test
    void traceparentEXrayChegamNoMesmoTrace() {
        TextMapPropagator propagador = PropagacaoDeTrace.propagador();
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        Context soW3c = propagador.extract(Context.root(), Map.of(
                "traceparent", "00-" + traceId + "-00f067aa0ba902b7-01"
        ), LEITOR);
        Context soXray = propagador.extract(Context.root(), Map.of(
                "X-Amzn-Trace-Id", "Root=1-4bf92f35-77b34da6a3ce929d0e0e4736;Parent=00f067aa0ba902b7;Sampled=0"
        ), LEITOR);

        assertThat(Span.fromContext(soW3c).getSpanContext().getTraceId()).isEqualTo(traceId);
        assertThat(Span.fromContext(soW3c).getSpanContext().isSampled()).isTrue();
        assertThat(Span.fromContext(soXray).getSpanContext().getTraceId()).isEqualTo(traceId);
        assertThat(Span.fromContext(soXray).getSpanContext().isSampled()).isFalse();
    }

    @Test
    void healthNaoAbreTrace() {
        var health = new ServerRequestObservationContext(
                new MockHttpServletRequest("GET", "/actuator/health/readiness"),
                new MockHttpServletResponse());
        assertThat(PropagacaoDeTrace.observar("http.server.requests", health)).isFalse();
        assertThat(PropagacaoDeTrace.observar("tasks.scheduled.execution", health)).isFalse();
        assertThat(PropagacaoDeTrace.observar("spring.security.authorizations", new Observation.Context())).isFalse();
    }
}

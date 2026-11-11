package br.com.fluxocaixa.lancamentos.config;

import io.micrometer.observation.Observation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PropagacaoDeTraceTest {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
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
    private static final TextMapSetter<Map<String, String>> GRAVADOR = (carrier, chave, valor) -> {
        if (carrier != null && valor != null) {
            carrier.put(chave, valor);
        }
    };

    @Test
    void headerXrayViraOTraceW3cEAmbosSaoInjetados() {
        TextMapPropagator propagador = PropagacaoDeTrace.propagador();
        Map<String, String> entrada = Map.of(
                "X-Amzn-Trace-Id", "Root=1-4bf92f35-77b34da6a3ce929d0e0e4736;Parent=00f067aa0ba902b7;Sampled=1"
        );
        Context pai = propagador.extract(Context.root(), entrada, LEITOR);
        SpanContext remoto = Span.fromContext(pai).getSpanContext();
        assertThat(remoto.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(remoto.isRemote()).isTrue();
        assertThat(remoto.isSampled()).isTrue();

        SdkTracerProvider provider = SdkTracerProvider.builder().build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
        Span filho = sdk.getTracer("teste").spanBuilder("filho").setParent(pai).startSpan();
        Map<String, String> saida = new HashMap<>();
        try (var escopo = filho.makeCurrent()) {
            propagador.inject(Context.current(), saida, GRAVADOR);
        } finally {
            filho.end();
            provider.close();
        }
        assertThat(saida.get("traceparent")).startsWith("00-" + TRACE_ID + "-");
        assertThat(saida.get("X-Amzn-Trace-Id")).contains("Root=1-4bf92f35-77b34da6a3ce929d0e0e4736");
        assertThat(saida.get("X-Amzn-Trace-Id")).contains("Sampled=1");
    }

    @Test
    void traceparentContinuaValendoQuandoNaoHaXray() {
        TextMapPropagator propagador = PropagacaoDeTrace.propagador();
        Map<String, String> entrada = Map.of(
                "traceparent", "00-" + TRACE_ID + "-00f067aa0ba902b7-01"
        );
        SpanContext remoto = Span.fromContext(propagador.extract(Context.root(), entrada, LEITOR)).getSpanContext();
        assertThat(remoto.getTraceId()).isEqualTo(TRACE_ID);
        assertThat(remoto.isSampled()).isTrue();
    }

    @Test
    void healthETarefaAgendadaNaoAbremTrace() {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        var health = new ServerRequestObservationContext(
                new MockHttpServletRequest("GET", "/actuator/health/readiness"), resposta);
        var api = new ServerRequestObservationContext(
                new MockHttpServletRequest("POST", "/api/v1/lancamentos"), resposta);

        assertThat(PropagacaoDeTrace.observar("http.server.requests", health)).isFalse();
        assertThat(PropagacaoDeTrace.observar("tasks.scheduled.execution", api)).isFalse();
        assertThat(PropagacaoDeTrace.observar("spring.security.filterchains", new Observation.Context())).isFalse();
        assertThat(PropagacaoDeTrace.observar("spring.security.authorizations", new Observation.Context())).isFalse();
        assertThat(PropagacaoDeTrace.observar("http.server.requests", api)).isTrue();
    }
}

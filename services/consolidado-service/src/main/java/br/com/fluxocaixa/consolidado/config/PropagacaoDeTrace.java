package br.com.fluxocaixa.consolidado.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * O Boot 4.0 só compõe W3C e B3 em {@code management.tracing.propagation.type}.
 * Um bean {@link TextMapPropagator} substitui esse composto
 * ({@code ConditionalOnMissingBean}). O X-Ray entra por último na extração:
 * sem o header, o contexto W3C permanece; com {@code X-Amzn-Trace-Id}, ele
 * vence. A injeção grava os dois. O sampler do Boot já é
 * {@code parentBased(traceIdRatioBased)}: a flag {@code Sampled} do header
 * manda, e a probabilidade só decide trace sem pai.
 */
@Configuration(proxyBeanMethods = false)
public class PropagacaoDeTrace {

    static final String TAREFA_AGENDADA = "tasks.scheduled.execution";
    static final String SEGURANCA = "spring.security.";
    static final String HEALTH = "/actuator/health";

    @Bean
    TextMapPropagator textMapPropagator() {
        return propagador();
    }

    @Bean
    ObservationPredicate semRuidoDeTrace() {
        return PropagacaoDeTrace::observar;
    }

    static TextMapPropagator propagador() {
        return TextMapPropagator.composite(
                W3CTraceContextPropagator.getInstance(),
                W3CBaggagePropagator.getInstance(),
                AwsXrayPropagator.getInstance()
        );
    }

    static boolean observar(String nome, Observation.Context contexto) {
        if (TAREFA_AGENDADA.equals(nome) || (nome != null && nome.startsWith(SEGURANCA))) {
            return false;
        }
        if (contexto instanceof ServerRequestObservationContext http) {
            String uri = http.getCarrier().getRequestURI();
            return uri == null || !uri.startsWith(HEALTH);
        }
        return true;
    }
}

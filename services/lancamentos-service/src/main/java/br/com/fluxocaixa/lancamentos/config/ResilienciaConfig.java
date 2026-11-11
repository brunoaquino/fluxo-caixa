package br.com.fluxocaixa.lancamentos.config;

import br.com.fluxocaixa.lancamentos.adapters.out.messaging.SnsPublicador;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.health.actuate.endpoint.SimpleStatusAggregator;
import org.springframework.boot.health.actuate.endpoint.StatusAggregator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ResilienciaConfig {

    @Bean(destroyMethod = "shutdown")
    ExecutorService publicacaoSnsExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("sns-publicacao-", 0).factory());
    }

    @Bean
    CircuitBreaker publicacaoSns(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(SnsPublicador.INSTANCIA);
    }

    /**
     * CIRCUIT_OPEN fica visível no health e perde para UP na agregação,
     * então o readiness (sem este componente) continua de pé.
     */
    @Bean
    StatusAggregator statusAggregator() {
        return new SimpleStatusAggregator(
                Status.DOWN,
                Status.OUT_OF_SERVICE,
                Status.UP,
                new Status("CIRCUIT_OPEN"),
                new Status("CIRCUIT_HALF_OPEN"),
                Status.UNKNOWN
        );
    }
}

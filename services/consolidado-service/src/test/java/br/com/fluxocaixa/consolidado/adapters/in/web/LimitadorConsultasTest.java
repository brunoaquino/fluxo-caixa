package br.com.fluxocaixa.consolidado.adapters.in.web;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitadorConsultasTest {

    @Test
    void desligadoNaoLimita() {
        LimitadorConsultas limitador = new LimitadorConsultas(false, null);
        assertThat(limitador.executar(() -> "ok")).isEqualTo("ok");
        assertThat(limitador.executar(() -> "ok")).isEqualTo("ok");
    }

    @Test
    void ligadoResponde429SemDetalheInterno() {
        RateLimiter rateLimiter = RateLimiter.of("consultas", RateLimiterConfig.custom()
                .limitForPeriod(1)
                .limitRefreshPeriod(Duration.ofMinutes(10))
                .timeoutDuration(Duration.ZERO)
                .build());
        LimitadorConsultas limitador = new LimitadorConsultas(true, rateLimiter);
        assertThat(limitador.executar(() -> "ok")).isEqualTo("ok");
        assertThatThrownBy(() -> limitador.executar(() -> "ok")).isInstanceOf(LimiteConsultasExcedidoException.class);

        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/consolidados/diario/2026-11-10");
        ResponseEntity<ProblemDetail> resposta = new TratadorErrosApi().limite(
                new LimiteConsultasExcedidoException(), request);
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().getDetail()).isEqualTo("Tente novamente em instantes");
        assertThat(resposta.getBody().getDetail()).doesNotContain("Exception");
    }
}

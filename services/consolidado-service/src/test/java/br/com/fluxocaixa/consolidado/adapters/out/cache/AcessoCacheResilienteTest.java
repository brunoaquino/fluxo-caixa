package br.com.fluxocaixa.consolidado.adapters.out.cache;

import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;
import br.com.fluxocaixa.consolidado.application.port.out.ConsultaSaldoPort;
import br.com.fluxocaixa.consolidado.application.servico.ConsultarSaldoDiarioService;
import br.com.fluxocaixa.consolidado.domain.model.LinhaRelatorio;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoAcumulado;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AcessoCacheResilienteTest {

    @Test
    void circuitoAbertoNaoChamaRedisEConsultaCaiNoBanco() {
        AtomicInteger chamadas = new AtomicInteger();
        CircuitBreaker circuito = CircuitBreaker.of("redis", CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build());
        CacheSaldoRedis.ArmazemCache delegado = armazem(chamadas, () -> {
            throw new IllegalStateException("redis fora");
        });
        AcessoCacheResiliente acesso = acesso(delegado, circuito, Duration.ofSeconds(1));
        try {
            assertThatThrownBy(() -> acesso.obter("a")).isInstanceOf(CacheIndisponivelException.class);
            assertThatThrownBy(() -> acesso.obter("a")).isInstanceOf(CacheIndisponivelException.class);
            assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.OPEN);

            int antes = chamadas.get();
            CacheSaldoRedis cache = new CacheSaldoRedis(acesso, JsonMapper.builder().build(), Duration.ofSeconds(30));
            assertThat(cache.obterDia(UUID.randomUUID(), LocalDate.of(2026, 11, 10))).isEmpty();
            assertThat(chamadas.get()).isEqualTo(antes);
            assertThatThrownBy(() -> acesso.obter("a")).isInstanceOf(CallNotPermittedException.class);

            UUID comerciante = UUID.randomUUID();
            LocalDate data = LocalDate.of(2026, 11, 10);
            ConsultarSaldoDiarioService servico = new ConsultarSaldoDiarioService(new ConsultaSaldoPort() {
                @Override
                public MovimentoAcumulado buscarDia(UUID comercianteId, LocalDate dia) {
                    return new MovimentoAcumulado(MovimentoDia.vazio(dia), new BigDecimal("80.00"));
                }

                @Override
                public List<LinhaRelatorio> buscarPeriodo(UUID comercianteId, LocalDate inicio, LocalDate fim) {
                    throw new UnsupportedOperationException();
                }
            }, cache);
            SaldoDiarioConsulta saldo = servico.consultar(comerciante, data);
            assertThat(saldo.saldoAcumulado()).isEqualByComparingTo("80.00");
        } finally {
            acesso.close();
        }
    }

    @Test
    void timeoutAcionaFalhaDoCircuito() {
        CircuitBreaker circuito = CircuitBreaker.ofDefaults("redis");
        AcessoCacheResiliente acesso = acesso(armazem(new AtomicInteger(), () -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return "lento";
        }), circuito, Duration.ofMillis(40));
        try {
            assertThatThrownBy(() -> acesso.obter("lento")).isInstanceOf(CacheIndisponivelException.class);
            assertThat(circuito.getMetrics().getNumberOfFailedCalls()).isPositive();
        } finally {
            acesso.close();
        }
    }

    private static AcessoCacheResiliente acesso(
            CacheSaldoRedis.ArmazemCache delegado,
            CircuitBreaker circuito,
            Duration timeout) {
        return new AcessoCacheResiliente(
                delegado,
                circuito,
                TimeLimiter.of(TimeLimiterConfig.custom()
                        .timeoutDuration(timeout)
                        .cancelRunningFuture(true)
                        .build()),
                Executors.newVirtualThreadPerTaskExecutor());
    }

    private static CacheSaldoRedis.ArmazemCache armazem(AtomicInteger chamadas, java.util.function.Supplier<String> obter) {
        return new CacheSaldoRedis.ArmazemCache() {
            @Override
            public String obter(String chave) {
                chamadas.incrementAndGet();
                return obter.get();
            }

            @Override
            public void guardar(String chave, String valor, Duration ttl) {
                throw new IllegalStateException("redis fora");
            }

            @Override
            public long incrementar(String chave) {
                throw new IllegalStateException("redis fora");
            }
        };
    }
}

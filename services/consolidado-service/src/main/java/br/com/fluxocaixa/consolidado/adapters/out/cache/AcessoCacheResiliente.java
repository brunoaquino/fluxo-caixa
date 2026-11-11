package br.com.fluxocaixa.consolidado.adapters.out.cache;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * Circuito e timeout em volta do Redis. A falha sobe para o cache-aside, que cai no banco.
 */
final class AcessoCacheResiliente implements CacheSaldoRedis.ArmazemCache {

    private final CacheSaldoRedis.ArmazemCache delegado;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final ExecutorService executor;

    AcessoCacheResiliente(
            CacheSaldoRedis.ArmazemCache delegado,
            CircuitBreaker circuitBreaker,
            TimeLimiter timeLimiter,
            ExecutorService executor) {
        this.delegado = delegado;
        this.circuitBreaker = circuitBreaker;
        this.timeLimiter = timeLimiter;
        this.executor = executor;
    }

    @Override
    public String obter(String chave) {
        return executar(() -> delegado.obter(chave));
    }

    @Override
    public void guardar(String chave, String valor, Duration ttl) {
        executar(() -> {
            delegado.guardar(chave, valor, ttl);
            return null;
        });
    }

    @Override
    public long incrementar(String chave) {
        Long valor = executar(() -> delegado.incrementar(chave));
        return valor == null ? 0L : valor;
    }

    void close() {
        executor.close();
    }

    private <T> T executar(Supplier<T> acao) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                try {
                    return timeLimiter.executeFutureSupplier(
                            () -> CompletableFuture.supplyAsync(acao, executor));
                } catch (Exception ex) {
                    if (ex instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new CacheIndisponivelException(ex);
                }
            });
        } catch (CallNotPermittedException ex) {
            throw ex;
        } catch (CacheIndisponivelException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new CacheIndisponivelException(ex);
        }
    }
}

package br.com.fluxocaixa.consolidado.adapters.in.web;

import br.com.fluxocaixa.consolidado.config.PropriedadesAplicacao;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class LimitadorConsultas {

    private final boolean habilitado;
    private final RateLimiter limitador;

    @Autowired
    public LimitadorConsultas(PropriedadesAplicacao propriedades, ObjectProvider<RateLimiterRegistry> registros) {
        this.habilitado = propriedades.resilience().rateLimiterHabilitado();
        if (!habilitado) {
            this.limitador = null;
            return;
        }
        RateLimiterRegistry registry = registros.getIfAvailable();
        if (registry == null) {
            throw new IllegalStateException("Rate limiter de consultas ativo sem RateLimiterRegistry");
        }
        this.limitador = registry.rateLimiter("consultas");
    }

    LimitadorConsultas(boolean habilitado, RateLimiter limitador) {
        this.habilitado = habilitado;
        this.limitador = limitador;
    }

    public <T> T executar(Supplier<T> acao) {
        if (!habilitado || limitador == null) {
            return acao.get();
        }
        try {
            return limitador.executeSupplier(acao::get);
        } catch (RequestNotPermitted ex) {
            throw new LimiteConsultasExcedidoException();
        }
    }
}

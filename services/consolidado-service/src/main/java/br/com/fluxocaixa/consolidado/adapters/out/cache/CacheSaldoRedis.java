package br.com.fluxocaixa.consolidado.adapters.out.cache;

import br.com.fluxocaixa.consolidado.application.consulta.RelatorioPeriodoConsulta;
import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;
import br.com.fluxocaixa.consolidado.application.port.out.CacheSaldoPort;
import br.com.fluxocaixa.consolidado.config.PropriedadesAplicacao;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Cache-aside com geração por comerciante. A chave de dados inclui a versão corrente.
 * Depois do commit do evento, a versão é incrementada e as leituras seguintes não reutilizam
 * o valor anterior. Chaves órfãs expiram no TTL. Se o incremento falhar, a leitura pode
 * permanecer obsoleta só até o TTL (consistência eventual de até 30s).
 * Qualquer falha do Redis degrada para o banco, sem propagar erro.
 */
@Component
public class CacheSaldoRedis implements CacheSaldoPort, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(CacheSaldoRedis.class);

    private final ArmazemCache armazem;
    private final JsonMapper json;
    private final Duration ttl;

    @Autowired
    public CacheSaldoRedis(
            StringRedisTemplate redis,
            JsonMapper json,
            PropriedadesAplicacao propriedades,
            CircuitBreakerRegistry circuitBreakers,
            TimeLimiterRegistry timeLimiters) {
        this(new AcessoCacheResiliente(
                        new ArmazemRedis(redis),
                        circuitBreakers.circuitBreaker("redis"),
                        timeLimiters.timeLimiter("redis"),
                        Executors.newVirtualThreadPerTaskExecutor()),
                json,
                propriedades.cache().ttl());
    }

    @Override
    public void destroy() {
        if (armazem instanceof AcessoCacheResiliente acesso) {
            acesso.close();
        }
    }

    CacheSaldoRedis(ArmazemCache armazem, JsonMapper json, Duration ttl) {
        this.armazem = armazem;
        this.json = json;
        this.ttl = ttl;
    }

    @Override
    public Optional<SaldoDiarioConsulta> obterDia(UUID comercianteId, LocalDate data) {
        try {
            return ler(chaveDia(comercianteId, data), SaldoDiarioConsulta.class);
        } catch (RuntimeException ex) {
            registrarFalha("Cache indisponível ao montar a chave do dia " + data, ex);
            return Optional.empty();
        }
    }

    @Override
    public void armazenarDia(UUID comercianteId, LocalDate data, SaldoDiarioConsulta consulta) {
        try {
            gravar(chaveDia(comercianteId, data), consulta);
        } catch (RuntimeException ex) {
            registrarFalha("Cache indisponível ao gravar o dia " + data, ex);
        }
    }

    @Override
    public Optional<RelatorioPeriodoConsulta> obterRelatorio(UUID comercianteId, LocalDate inicio, LocalDate fim) {
        try {
            return ler(chaveRelatorio(comercianteId, inicio, fim), RelatorioPeriodoConsulta.class);
        } catch (RuntimeException ex) {
            registrarFalha("Cache indisponível ao montar a chave do relatório " + inicio + "-" + fim, ex);
            return Optional.empty();
        }
    }

    @Override
    public void armazenarRelatorio(
            UUID comercianteId,
            LocalDate inicio,
            LocalDate fim,
            RelatorioPeriodoConsulta relatorio) {
        try {
            gravar(chaveRelatorio(comercianteId, inicio, fim), relatorio);
        } catch (RuntimeException ex) {
            registrarFalha("Cache indisponível ao gravar o relatório " + inicio + "-" + fim, ex);
        }
    }

    @Override
    public void invalidar(UUID comercianteId) {
        try {
            armazem.incrementar(chaveVersao(comercianteId));
        } catch (RuntimeException ex) {
            registrarFalha(
                    "Falha ao invalidar o cache do comerciante " + comercianteId + "; a consistência fica limitada ao TTL",
                    ex);
        }
    }

    private <T> Optional<T> ler(String chave, Class<T> tipo) {
        try {
            String valor = armazem.obter(chave);
            if (valor == null || valor.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(json.readValue(valor, tipo));
        } catch (RuntimeException ex) {
            registrarFalha("Cache indisponível ou ilegível na chave " + chave + "; a leitura segue pelo banco", ex);
            return Optional.empty();
        }
    }

    private void gravar(String chave, Object valor) {
        try {
            armazem.guardar(chave, json.writeValueAsString(valor), ttl);
        } catch (RuntimeException ex) {
            registrarFalha("Falha ao gravar o cache da chave " + chave + "; a resposta foi servida pelo banco", ex);
        }
    }

    private static void registrarFalha(String mensagem, RuntimeException ex) {
        if (circuitoAberto(ex)) {
            log.debug("{}: circuito do Redis aberto; a leitura segue pelo banco", mensagem);
            return;
        }
        log.warn(mensagem, ex);
    }

    private static boolean circuitoAberto(Throwable ex) {
        Throwable atual = ex;
        while (atual != null) {
            if (atual instanceof CallNotPermittedException) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }

    private String chaveDia(UUID comercianteId, LocalDate data) {
        return "consolidado:dia:" + comercianteId + ":" + versao(comercianteId) + ":" + data;
    }

    private String chaveRelatorio(UUID comercianteId, LocalDate inicio, LocalDate fim) {
        return "consolidado:relatorio:" + comercianteId + ":" + versao(comercianteId) + ":" + inicio + ":" + fim;
    }

    private long versao(UUID comercianteId) {
        String valor = armazem.obter(chaveVersao(comercianteId));
        if (valor == null || valor.isBlank()) {
            return 0L;
        }
        return Long.parseLong(valor);
    }

    private static String chaveVersao(UUID comercianteId) {
        return "consolidado:versao:" + comercianteId;
    }

    interface ArmazemCache {

        String obter(String chave);

        void guardar(String chave, String valor, Duration ttl);

        long incrementar(String chave);
    }

    static final class ArmazemRedis implements ArmazemCache {

        private final StringRedisTemplate redis;

        ArmazemRedis(StringRedisTemplate redis) {
            this.redis = redis;
        }

        @Override
        public String obter(String chave) {
            return redis.opsForValue().get(chave);
        }

        @Override
        public void guardar(String chave, String valor, Duration ttl) {
            redis.opsForValue().set(chave, valor, ttl);
        }

        @Override
        public long incrementar(String chave) {
            Long valor = redis.opsForValue().increment(chave);
            return valor == null ? 0L : valor;
        }
    }
}

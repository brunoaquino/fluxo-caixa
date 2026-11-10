package br.com.fluxocaixa.consolidado.adapters.out.cache;

import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CacheSaldoRedisTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final UUID comerciante = UUID.randomUUID();
    private final LocalDate data = LocalDate.of(2026, 11, 10);

    @Test
    void redisIndisponivelNaoQuebraLeituraNemInvalidacao() {
        CacheSaldoRedis.ArmazemCache fora = new CacheSaldoRedis.ArmazemCache() {
            @Override
            public String obter(String chave) {
                throw new IllegalStateException("redis fora");
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
        CacheSaldoRedis cache = new CacheSaldoRedis(fora, json, Duration.ofSeconds(30));
        SaldoDiarioConsulta consulta = SaldoDiarioConsulta.de(MovimentoDia.vazio(data), BigDecimal.ZERO);

        assertThat(cache.obterDia(comerciante, data)).isEmpty();
        assertThat(cache.obterRelatorio(comerciante, data, data.plusDays(1))).isEmpty();
        assertThatCode(() -> cache.armazenarDia(comerciante, data, consulta)).doesNotThrowAnyException();
        assertThatCode(() -> cache.armazenarRelatorio(comerciante, data, data, null)).doesNotThrowAnyException();
        assertThatCode(() -> cache.invalidar(comerciante)).doesNotThrowAnyException();
    }

    @Test
    void invalidarTrocaAVersaoEDescartaOValorAnterior() {
        Memoria memoria = new Memoria();
        CacheSaldoRedis cache = new CacheSaldoRedis(memoria, json, Duration.ofSeconds(30));
        SaldoDiarioConsulta consulta = SaldoDiarioConsulta.de(MovimentoDia.vazio(data), new BigDecimal("10.00"));

        cache.armazenarDia(comerciante, data, consulta);
        assertThat(cache.obterDia(comerciante, data)).contains(consulta);

        cache.invalidar(comerciante);

        assertThat(cache.obterDia(comerciante, data)).isEmpty();
    }

    private static final class Memoria implements CacheSaldoRedis.ArmazemCache {

        private final Map<String, String> dados = new HashMap<>();

        @Override
        public String obter(String chave) {
            return dados.get(chave);
        }

        @Override
        public void guardar(String chave, String valor, Duration ttl) {
            dados.put(chave, valor);
        }

        @Override
        public long incrementar(String chave) {
            long atual = dados.containsKey(chave) ? Long.parseLong(dados.get(chave)) : 0L;
            long proximo = atual + 1;
            dados.put(chave, Long.toString(proximo));
            return proximo;
        }
    }
}

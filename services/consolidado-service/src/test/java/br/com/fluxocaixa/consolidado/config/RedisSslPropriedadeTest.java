package br.com.fluxocaixa.consolidado.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisSslPropriedadeTest {

    @Test
    void ausenteOuFalsoDesligaTls() throws Exception {
        assertThat(propriedades(Map.of()).getSsl().isEnabled()).isFalse();
        assertThat(propriedades(Map.of("REDIS_SSL", "false")).getSsl().isEnabled()).isFalse();
    }

    @Test
    void redisSslTrueLigaTlsSemExigirSenha() throws Exception {
        DataRedisProperties propriedades = propriedades(Map.of("REDIS_SSL", "true"));
        assertThat(propriedades.getSsl().isEnabled()).isTrue();
        assertThat(propriedades.getPassword()).isNull();
        assertThat(propriedades.getUsername()).isNull();
    }

    private static DataRedisProperties propriedades(Map<String, Object> ambiente) throws Exception {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("ambiente", ambiente));
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(env.getPropertySources()::addLast);
        return Binder.get(env).bind("spring.data.redis", DataRedisProperties.class).get();
    }
}

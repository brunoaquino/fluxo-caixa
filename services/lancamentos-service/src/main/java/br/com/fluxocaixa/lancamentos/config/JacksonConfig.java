package br.com.fluxocaixa.lancamentos.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer rejeitarCamposDesconhecidos() {
        return new RejeitarCamposDesconhecidos();
    }

    static final class RejeitarCamposDesconhecidos implements JsonMapperBuilderCustomizer, Ordered {

        @Override
        public void customize(JsonMapper.Builder builder) {
            builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }
}

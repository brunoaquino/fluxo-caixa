package br.com.fluxocaixa.consolidado.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@TestConfiguration
@Profile("test")
public class JwtDecoderTesteConfig {

    @Bean
    JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException("Use o post processor jwt() do MockMvc");
        };
    }
}

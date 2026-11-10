package br.com.fluxocaixa.lancamentos.config;

import br.com.fluxocaixa.lancamentos.application.port.out.GeradorDeIds;
import br.com.fluxocaixa.lancamentos.application.port.out.Relogio;
import br.com.fluxocaixa.lancamentos.domain.identidade.UuidV7;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;

@Configuration
public class TempoConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    Relogio relogio(Clock clock) {
        return () -> Instant.now(clock);
    }

    @Bean
    GeradorDeIds geradorDeIds() {
        return UuidV7::gerar;
    }
}

package br.com.fluxocaixa.consolidado.adapters.in.web;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class LimitadorPassThroughConfig {

    @Bean
    LimitadorConsultas limitadorConsultas() {
        return new LimitadorConsultas(false, null);
    }
}

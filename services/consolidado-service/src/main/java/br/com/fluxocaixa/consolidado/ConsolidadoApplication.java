package br.com.fluxocaixa.consolidado;

import br.com.fluxocaixa.consolidado.config.PropriedadesAplicacao;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PropriedadesAplicacao.class)
public class ConsolidadoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsolidadoApplication.class, args);
    }
}

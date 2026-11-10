package br.com.fluxocaixa.consolidado.config;

import br.com.fluxocaixa.consolidado.application.port.in.AplicarLancamentoUseCase;
import br.com.fluxocaixa.consolidado.application.port.in.ConsultarRelatorioUseCase;
import br.com.fluxocaixa.consolidado.application.port.in.ConsultarSaldoDiarioUseCase;
import br.com.fluxocaixa.consolidado.application.port.out.AplicacaoLancamentoPort;
import br.com.fluxocaixa.consolidado.application.port.out.CacheSaldoPort;
import br.com.fluxocaixa.consolidado.application.port.out.ConsultaSaldoPort;
import br.com.fluxocaixa.consolidado.application.servico.AplicarLancamentoService;
import br.com.fluxocaixa.consolidado.application.servico.ConsultarRelatorioService;
import br.com.fluxocaixa.consolidado.application.servico.ConsultarSaldoDiarioService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ConfiguracaoAplicacao {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    AplicarLancamentoUseCase aplicarLancamentoUseCase(
            AplicacaoLancamentoPort aplicacao,
            CacheSaldoPort cache,
            Clock relogio) {
        return new AplicarLancamentoService(aplicacao, cache, relogio);
    }

    @Bean
    ConsultarSaldoDiarioUseCase consultarSaldoDiarioUseCase(ConsultaSaldoPort consulta, CacheSaldoPort cache) {
        return new ConsultarSaldoDiarioService(consulta, cache);
    }

    @Bean
    ConsultarRelatorioUseCase consultarRelatorioUseCase(ConsultaSaldoPort consulta, CacheSaldoPort cache) {
        return new ConsultarRelatorioService(consulta, cache);
    }
}

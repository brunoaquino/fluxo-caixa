package br.com.fluxocaixa.lancamentos.application.port.in;

import br.com.fluxocaixa.lancamentos.application.comando.EstornarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.resultado.ResultadoEscrita;

public interface EstornarLancamento {

    ResultadoEscrita executar(EstornarLancamentoComando comando);
}

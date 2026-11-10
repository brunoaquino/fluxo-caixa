package br.com.fluxocaixa.lancamentos.application.port.in;

import br.com.fluxocaixa.lancamentos.application.comando.RegistrarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.resultado.ResultadoEscrita;

public interface RegistrarLancamento {

    ResultadoEscrita executar(RegistrarLancamentoComando comando);
}

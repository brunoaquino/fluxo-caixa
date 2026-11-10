package br.com.fluxocaixa.consolidado.application.port.in;

import br.com.fluxocaixa.consolidado.application.comando.ComandoAplicarLancamento;
import br.com.fluxocaixa.consolidado.application.resultado.ResultadoAplicacao;

public interface AplicarLancamentoUseCase {

    ResultadoAplicacao executar(ComandoAplicarLancamento comando);
}

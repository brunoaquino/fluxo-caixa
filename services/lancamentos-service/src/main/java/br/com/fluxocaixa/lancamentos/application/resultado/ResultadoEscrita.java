package br.com.fluxocaixa.lancamentos.application.resultado;

import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;

public record ResultadoEscrita(Lancamento lancamento, boolean reutilizado) {
}

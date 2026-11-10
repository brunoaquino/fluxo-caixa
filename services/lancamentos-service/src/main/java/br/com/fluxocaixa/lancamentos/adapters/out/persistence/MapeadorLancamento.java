package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;

final class MapeadorLancamento {

    private MapeadorLancamento() {
    }

    static LancamentoJpa paraJpa(Lancamento lancamento) {
        return new LancamentoJpa(
                lancamento.id(),
                lancamento.comercianteId(),
                lancamento.tipo(),
                lancamento.valor().valor(),
                Dinheiro.MOEDA,
                lancamento.dataLancamento(),
                lancamento.descricao(),
                lancamento.categoria(),
                lancamento.lancamentoEstornadoId(),
                lancamento.criadoEm()
        );
    }

    static Lancamento paraDominio(LancamentoJpa entidade) {
        return new Lancamento(
                entidade.getId(),
                entidade.getComercianteId(),
                entidade.getTipo(),
                new Dinheiro(entidade.getValor()),
                entidade.getDataLancamento(),
                entidade.getDescricao(),
                entidade.getCategoria(),
                entidade.getLancamentoEstornadoId(),
                entidade.getCriadoEm()
        );
    }
}

package br.com.fluxocaixa.lancamentos.domain.excecao;

/**
 * Erro de invariante do contexto de lançamentos.
 * Novas variantes precisam entrar no {@code permits} e nos switches que tratam a hierarquia.
 */
public abstract sealed class ExcecaoDeDominio extends RuntimeException
        permits ValorMonetarioInvalidoException,
                TextoInvalidoException,
                EstornoDeEstornoNaoPermitidoException,
                LancamentoJaEstornadoException {

    protected ExcecaoDeDominio(String mensagem) {
        super(mensagem);
    }
}

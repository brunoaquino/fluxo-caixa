package br.com.fluxocaixa.lancamentos.adapters.in.web.excecao;

public class ComercianteNaoIdentificadoException extends RuntimeException {

    public ComercianteNaoIdentificadoException(String mensagem) {
        super(mensagem);
    }
}

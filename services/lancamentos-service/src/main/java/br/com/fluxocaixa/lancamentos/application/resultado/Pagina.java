package br.com.fluxocaixa.lancamentos.application.resultado;

import java.util.List;

public record Pagina<T>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas
) {
    public Pagina {
        conteudo = List.copyOf(conteudo);
    }
}

package br.com.fluxocaixa.lancamentos.adapters.in.web.dto;

import java.util.List;

public record PaginaResponse<T>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas
) {
}

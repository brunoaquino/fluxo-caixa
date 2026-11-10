package br.com.fluxocaixa.lancamentos.adapters.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Propaga ou gera o identificador de correlação e o deixa no MDC para o log estruturado.
 */
public class FiltroCorrelacao extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Correlation-Id";
    public static final String MDC_CORRELACAO = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = request.getHeader(CABECALHO);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        } else {
            correlationId = correlationId.trim();
        }
        response.setHeader(CABECALHO, correlationId);
        MDC.put(MDC_CORRELACAO, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELACAO);
        }
    }
}

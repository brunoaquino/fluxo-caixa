package br.com.fluxocaixa.lancamentos.adapters.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Aceita {@code X-Correlation-Id} só quando é um UUID. Ausente ou inválido
 * gera outro, devolve no response e coloca no MDC.
 */
public class FiltroCorrelacao extends OncePerRequestFilter {

    public static final String CABECALHO = "X-Correlation-Id";
    public static final String MDC_CORRELACAO = "correlationId";

    private static final Pattern UUID_CANONICO = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String correlationId = resolver(request.getHeader(CABECALHO));
        response.setHeader(CABECALHO, correlationId);
        MDC.put(MDC_CORRELACAO, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CORRELACAO);
            MdcDeNegocio.limpar();
        }
    }

    static String resolver(String header) {
        if (header != null) {
            String candidato = header.trim();
            if (UUID_CANONICO.matcher(candidato).matches()) {
                return UUID.fromString(candidato).toString();
            }
        }
        return UUID.randomUUID().toString();
    }
}

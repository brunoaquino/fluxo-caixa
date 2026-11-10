package br.com.fluxocaixa.lancamentos.config;

import br.com.fluxocaixa.lancamentos.adapters.in.web.FiltroCorrelacao;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class EscritorDeProblema {

    private static final String TIPO_BASE = "https://fluxocaixa.local/problemas/";

    private final JsonMapper jsonMapper;

    public EscritorDeProblema(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void escrever(HttpServletResponse response, HttpStatus status, String tipo, String titulo, String detalhe)
            throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setType(URI.create(TIPO_BASE + tipo));
        String correlationId = MDC.get(FiltroCorrelacao.MDC_CORRELACAO);
        if (correlationId != null) {
            problema.setProperty("correlationId", correlationId);
        }
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), problema);
    }
}

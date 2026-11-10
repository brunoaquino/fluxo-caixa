package br.com.fluxocaixa.consolidado.adapters.in.web;

import br.com.fluxocaixa.consolidado.domain.exception.PeriodoConsultaInvalidoException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;

@RestControllerAdvice
public class TratadorErrosApi {

    private static final Logger log = LoggerFactory.getLogger(TratadorErrosApi.class);

    @ExceptionHandler(PeriodoConsultaInvalidoException.class)
    ResponseEntity<ProblemDetail> periodo(PeriodoConsultaInvalidoException ex, HttpServletRequest request) {
        String detalhe = switch (ex.motivo()) {
            case DATA_OBRIGATORIA -> ex.motivo().mensagem();
            case INICIO_APOS_FIM -> ex.motivo().mensagem();
            case INTERVALO_MAIOR_QUE_366 -> ex.motivo().mensagem();
        };
        return resposta(
                HttpStatus.BAD_REQUEST,
                "Período inválido",
                detalhe,
                URI.create("https://fluxocaixa.com.br/problems/periodo-invalido"),
                request);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> parametroInvalido(Exception ex, HttpServletRequest request) {
        return resposta(
                HttpStatus.BAD_REQUEST,
                "Requisição inválida",
                "Verifique as datas informadas. Use o formato YYYY-MM-DD",
                URI.create("https://fluxocaixa.com.br/problems/requisicao-invalida"),
                request);
    }

    @ExceptionHandler(ComercianteNaoIdentificadoException.class)
    ResponseEntity<ProblemDetail> comerciante(ComercianteNaoIdentificadoException ex, HttpServletRequest request) {
        return resposta(
                HttpStatus.FORBIDDEN,
                "Comerciante não identificado",
                ex.getMessage(),
                URI.create("https://fluxocaixa.com.br/problems/comerciante-nao-identificado"),
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception ex, HttpServletRequest request) {
        log.error("Falha inesperada na API de consolidado", ex);
        return resposta(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno",
                "Não foi possível consultar o consolidado",
                URI.create("https://fluxocaixa.com.br/problems/erro-interno"),
                request);
    }

    private static ResponseEntity<ProblemDetail> resposta(
            HttpStatus status,
            String titulo,
            String detalhe,
            URI tipo,
            HttpServletRequest request) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setType(tipo);
        problema.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problema);
    }
}

package br.com.fluxocaixa.lancamentos.adapters.in.web;

import br.com.fluxocaixa.lancamentos.adapters.in.web.excecao.ChaveIdempotenciaInvalidaException;
import br.com.fluxocaixa.lancamentos.adapters.in.web.excecao.ComercianteNaoIdentificadoException;
import br.com.fluxocaixa.lancamentos.adapters.in.web.excecao.PaginacaoInvalidaException;
import br.com.fluxocaixa.lancamentos.application.excecao.IdempotenciaConflitanteException;
import br.com.fluxocaixa.lancamentos.application.excecao.IdempotenciaDuplicadaException;
import br.com.fluxocaixa.lancamentos.application.excecao.LancamentoNaoEncontradoException;
import br.com.fluxocaixa.lancamentos.domain.excecao.EstornoDeEstornoNaoPermitidoException;
import br.com.fluxocaixa.lancamentos.domain.excecao.ExcecaoDeDominio;
import br.com.fluxocaixa.lancamentos.domain.excecao.LancamentoJaEstornadoException;
import br.com.fluxocaixa.lancamentos.domain.excecao.TextoInvalidoException;
import br.com.fluxocaixa.lancamentos.domain.excecao.ValorMonetarioInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Map;

@RestControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);
    private static final String TIPO_BASE = "https://fluxocaixa.local/problemas/";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validacao(MethodArgumentNotValidException ex) {
        ProblemDetail problema = problema(
                HttpStatus.BAD_REQUEST,
                "validacao",
                "Erro de validação",
                "A requisição contém campos inválidos"
        );
        problema.setProperty(
                "erros",
                ex.getBindingResult().getFieldErrors().stream()
                        .map(erro -> Map.of(
                                "campo", erro.getField(),
                                "mensagem", erro.getDefaultMessage() == null ? "inválido" : erro.getDefaultMessage()
                        ))
                        .toList()
        );
        return problema;
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            ChaveIdempotenciaInvalidaException.class,
            PaginacaoInvalidaException.class
    })
    ProblemDetail requisicaoInvalida(Exception ex) {
        String detalhe = switch (ex) {
            case ChaveIdempotenciaInvalidaException chave -> chave.getMessage();
            case PaginacaoInvalidaException paginacao -> paginacao.getMessage();
            case MissingServletRequestParameterException parametro ->
                    "Parâmetro obrigatório ausente: " + parametro.getParameterName();
            case MethodArgumentTypeMismatchException tipo -> "Valor inválido para " + tipo.getName();
            case HttpMessageNotReadableException ignorado -> "Corpo da requisição inválido";
            default -> "Requisição inválida";
        };
        return problema(HttpStatus.BAD_REQUEST, "requisicao-invalida", "Requisição inválida", detalhe);
    }

    @ExceptionHandler(ExcecaoDeDominio.class)
    ProblemDetail dominio(ExcecaoDeDominio ex) {
        return switch (ex) {
            case ValorMonetarioInvalidoException valor ->
                    problema(HttpStatus.BAD_REQUEST, "validacao", "Valor inválido", valor.getMessage());
            case TextoInvalidoException texto ->
                    problema(HttpStatus.BAD_REQUEST, "validacao", "Texto inválido", texto.getMessage());
            case EstornoDeEstornoNaoPermitidoException estorno ->
                    problema(HttpStatus.CONFLICT, "regra-de-negocio", "Estorno de estorno não permitido", estorno.getMessage());
            case LancamentoJaEstornadoException jaEstornado ->
                    problema(HttpStatus.CONFLICT, "regra-de-negocio", "Lançamento já estornado", jaEstornado.getMessage());
        };
    }

    @ExceptionHandler(LancamentoNaoEncontradoException.class)
    ProblemDetail naoEncontrado(LancamentoNaoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "lancamento-nao-encontrado", "Lançamento não encontrado", ex.getMessage());
    }

    @ExceptionHandler(IdempotenciaConflitanteException.class)
    ProblemDetail idempotencia(IdempotenciaConflitanteException ex) {
        return problema(HttpStatus.CONFLICT, "idempotencia", "Conflito de idempotência", ex.getMessage());
    }

    @ExceptionHandler(IdempotenciaDuplicadaException.class)
    ProblemDetail idempotenciaEmDisputa(IdempotenciaDuplicadaException ex) {
        return problema(HttpStatus.CONFLICT, "idempotencia", "Conflito de idempotência", ex.getMessage());
    }

    @ExceptionHandler(ComercianteNaoIdentificadoException.class)
    ProblemDetail comerciante(ComercianteNaoIdentificadoException ex) {
        return problema(HttpStatus.FORBIDDEN, "comerciante-ausente", "Comerciante não identificado", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inesperado(Exception ex) {
        log.error("Falha inesperada ao processar a requisição", ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "erro-interno", "Erro interno", "Não foi possível concluir a operação");
    }

    private static ProblemDetail problema(HttpStatus status, String tipo, String titulo, String detalhe) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setType(URI.create(TIPO_BASE + tipo));
        String correlationId = MDC.get(FiltroCorrelacao.MDC_CORRELACAO);
        if (correlationId != null) {
            problema.setProperty("correlationId", correlationId);
        }
        return problema;
    }
}

package br.com.fluxocaixa.lancamentos.adapters.in.web;

import br.com.fluxocaixa.lancamentos.adapters.in.web.dto.EstornarLancamentoRequest;
import br.com.fluxocaixa.lancamentos.adapters.in.web.dto.LancamentoResponse;
import br.com.fluxocaixa.lancamentos.adapters.in.web.dto.PaginaResponse;
import br.com.fluxocaixa.lancamentos.adapters.in.web.dto.RegistrarLancamentoRequest;
import br.com.fluxocaixa.lancamentos.adapters.in.web.excecao.ChaveIdempotenciaInvalidaException;
import br.com.fluxocaixa.lancamentos.adapters.in.web.excecao.PaginacaoInvalidaException;
import br.com.fluxocaixa.lancamentos.application.comando.EstornarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.comando.RegistrarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.port.in.ConsultarLancamento;
import br.com.fluxocaixa.lancamentos.application.port.in.EstornarLancamento;
import br.com.fluxocaixa.lancamentos.application.port.in.ListarLancamentos;
import br.com.fluxocaixa.lancamentos.application.port.in.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.resultado.Pagina;
import br.com.fluxocaixa.lancamentos.application.resultado.ResultadoEscrita;
import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lancamentos")
@Tag(name = "Lançamentos", description = "Créditos, débitos e estornos do comerciante autenticado")
public class LancamentoController {

    private static final int TAMANHO_PADRAO = 50;
    private static final int TAMANHO_MAXIMO = 100;
    private static final int PAGINA_MAXIMA = 1000;

    private final RegistrarLancamento registrarLancamento;
    private final ConsultarLancamento consultarLancamento;
    private final ListarLancamentos listarLancamentos;
    private final EstornarLancamento estornarLancamento;
    private final ComercianteAutenticado comercianteAutenticado;

    public LancamentoController(
            RegistrarLancamento registrarLancamento,
            ConsultarLancamento consultarLancamento,
            ListarLancamentos listarLancamentos,
            EstornarLancamento estornarLancamento,
            ComercianteAutenticado comercianteAutenticado
    ) {
        this.registrarLancamento = registrarLancamento;
        this.consultarLancamento = consultarLancamento;
        this.listarLancamentos = listarLancamentos;
        this.estornarLancamento = estornarLancamento;
        this.comercianteAutenticado = comercianteAutenticado;
    }

    @PostMapping
    @Operation(summary = "Registra um crédito ou débito")
    public ResponseEntity<LancamentoResponse> registrar(
            @RequestHeader(name = "Idempotency-Key", required = false) String chaveIdempotencia,
            @Valid @RequestBody RegistrarLancamentoRequest requisicao
    ) {
        UUID comercianteId = comercianteAutenticado.exigir();
        MdcDeNegocio.comerciante(comercianteId);
        ResultadoEscrita resultado = registrarLancamento.executar(new RegistrarLancamentoComando(
                comercianteId,
                interpretarChave(chaveIdempotencia),
                requisicao.tipo(),
                requisicao.valor(),
                requisicao.dataLancamento(),
                requisicao.descricao(),
                requisicao.categoria()
        ));
        MdcDeNegocio.lancamento(resultado.lancamento().id());
        return responderEscrita(resultado);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um lançamento do comerciante autenticado")
    public LancamentoResponse detalhar(@PathVariable UUID id) {
        UUID comercianteId = comercianteAutenticado.exigir();
        MdcDeNegocio.comerciante(comercianteId);
        MdcDeNegocio.lancamento(id);
        return responder(consultarLancamento.executar(comercianteId, id));
    }

    @GetMapping
    @Operation(summary = "Lista os lançamentos de uma data de competência")
    public PaginaResponse<LancamentoResponse> listar(
            @RequestParam LocalDate data,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + TAMANHO_PADRAO) int size
    ) {
        if (page < 0 || page > PAGINA_MAXIMA || size < 1 || size > TAMANHO_MAXIMO) {
            throw new PaginacaoInvalidaException();
        }
        UUID comercianteId = comercianteAutenticado.exigir();
        MdcDeNegocio.comerciante(comercianteId);
        Pagina<Lancamento> pagina = listarLancamentos.executar(comercianteId, data, page, size);
        return new PaginaResponse<>(
                pagina.conteudo().stream().map(this::responder).toList(),
                pagina.pagina(),
                pagina.tamanho(),
                pagina.totalElementos(),
                pagina.totalPaginas()
        );
    }

    @PostMapping("/{id}/estornos")
    @Operation(summary = "Estorna um lançamento, gerando o lançamento oposto na mesma data")
    public ResponseEntity<LancamentoResponse> estornar(
            @PathVariable UUID id,
            @RequestHeader(name = "Idempotency-Key", required = false) String chaveIdempotencia,
            @Valid @RequestBody(required = false) EstornarLancamentoRequest requisicao
    ) {
        UUID comercianteId = comercianteAutenticado.exigir();
        MdcDeNegocio.comerciante(comercianteId);
        MdcDeNegocio.lancamento(id);
        String motivo = requisicao == null ? null : requisicao.motivo();
        ResultadoEscrita resultado = estornarLancamento.executar(new EstornarLancamentoComando(
                comercianteId,
                id,
                interpretarChave(chaveIdempotencia),
                motivo
        ));
        MdcDeNegocio.lancamento(resultado.lancamento().id());
        return responderEscrita(resultado);
    }

    private ResponseEntity<LancamentoResponse> responderEscrita(ResultadoEscrita resultado) {
        LancamentoResponse corpo = responder(resultado.lancamento());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/lancamentos/{id}")
                .buildAndExpand(resultado.lancamento().id())
                .toUri();
        if (resultado.reutilizado()) {
            return ResponseEntity.ok().location(location).body(corpo);
        }
        return ResponseEntity.created(location).body(corpo);
    }

    private LancamentoResponse responder(Lancamento lancamento) {
        return new LancamentoResponse(
                lancamento.id(),
                lancamento.tipo(),
                lancamento.valor().formatado(),
                Dinheiro.MOEDA,
                lancamento.dataLancamento(),
                lancamento.descricao(),
                lancamento.categoria(),
                lancamento.lancamentoEstornadoId(),
                lancamento.criadoEm()
        );
    }

    private static UUID interpretarChave(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException ex) {
            throw new ChaveIdempotenciaInvalidaException();
        }
    }
}

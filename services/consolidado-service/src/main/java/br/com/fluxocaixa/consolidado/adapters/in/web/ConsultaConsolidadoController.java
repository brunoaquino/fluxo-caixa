package br.com.fluxocaixa.consolidado.adapters.in.web;

import br.com.fluxocaixa.consolidado.application.port.in.ConsultarRelatorioUseCase;
import br.com.fluxocaixa.consolidado.application.port.in.ConsultarSaldoDiarioUseCase;
import br.com.fluxocaixa.consolidado.config.ConfiguracaoOpenApi;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/consolidados")
@Tag(name = "Consolidados", description = "Saldo diário consolidado do comerciante autenticado")
@SecurityRequirement(name = ConfiguracaoOpenApi.ESQUEMA)
public class ConsultaConsolidadoController {

    private final ConsultarSaldoDiarioUseCase consultarSaldoDiario;
    private final ConsultarRelatorioUseCase consultarRelatorio;
    private final ComercianteAutenticado comerciante;
    private final LimitadorConsultas limitador;

    public ConsultaConsolidadoController(
            ConsultarSaldoDiarioUseCase consultarSaldoDiario,
            ConsultarRelatorioUseCase consultarRelatorio,
            ComercianteAutenticado comerciante,
            LimitadorConsultas limitador) {
        this.consultarSaldoDiario = consultarSaldoDiario;
        this.consultarRelatorio = consultarRelatorio;
        this.comerciante = comerciante;
        this.limitador = limitador;
    }

    @GetMapping("/diario/{data}")
    @Operation(summary = "Saldo consolidado do dia, incluindo o saldo acumulado")
    public SaldoDiarioResponse dia(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return limitador.executar(() ->
                MapeadorResposta.paraResposta(consultarSaldoDiario.consultar(comerciante.atual(), data)));
    }

    @GetMapping("/diario")
    @Operation(summary = "Relatório do período, limitado a 366 dias")
    public RelatorioPeriodoResponse periodo(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return limitador.executar(() ->
                MapeadorResposta.paraResposta(consultarRelatorio.consultar(comerciante.atual(), inicio, fim)));
    }
}

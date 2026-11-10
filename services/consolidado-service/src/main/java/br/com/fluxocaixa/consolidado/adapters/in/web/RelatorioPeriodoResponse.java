package br.com.fluxocaixa.consolidado.adapters.in.web;

import java.time.LocalDate;
import java.util.List;

public record RelatorioPeriodoResponse(LocalDate inicio, LocalDate fim, List<SaldoDiarioResponse> dias) {
}

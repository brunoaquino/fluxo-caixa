package br.com.fluxocaixa.consolidado.application.consulta;

import java.time.LocalDate;
import java.util.List;

public record RelatorioPeriodoConsulta(LocalDate inicio, LocalDate fim, List<SaldoDiarioConsulta> dias) {
}

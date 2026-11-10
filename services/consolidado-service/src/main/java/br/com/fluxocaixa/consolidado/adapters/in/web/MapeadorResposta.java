package br.com.fluxocaixa.consolidado.adapters.in.web;

import br.com.fluxocaixa.consolidado.application.consulta.RelatorioPeriodoConsulta;
import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;

public final class MapeadorResposta {

    private MapeadorResposta() {
    }

    public static SaldoDiarioResponse paraResposta(SaldoDiarioConsulta consulta) {
        return new SaldoDiarioResponse(
                consulta.data(),
                consulta.totalCreditos(),
                consulta.totalDebitos(),
                consulta.saldo(),
                consulta.saldoAcumulado(),
                consulta.quantidadeLancamentos(),
                consulta.atualizadoEm());
    }

    public static RelatorioPeriodoResponse paraResposta(RelatorioPeriodoConsulta relatorio) {
        return new RelatorioPeriodoResponse(
                relatorio.inicio(),
                relatorio.fim(),
                relatorio.dias().stream().map(MapeadorResposta::paraResposta).toList());
    }
}

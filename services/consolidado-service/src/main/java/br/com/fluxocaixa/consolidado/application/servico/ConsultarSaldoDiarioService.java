package br.com.fluxocaixa.consolidado.application.servico;

import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;
import br.com.fluxocaixa.consolidado.application.port.in.ConsultarSaldoDiarioUseCase;
import br.com.fluxocaixa.consolidado.application.port.out.CacheSaldoPort;
import br.com.fluxocaixa.consolidado.application.port.out.ConsultaSaldoPort;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoAcumulado;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class ConsultarSaldoDiarioService implements ConsultarSaldoDiarioUseCase {

    private final ConsultaSaldoPort consulta;
    private final CacheSaldoPort cache;

    public ConsultarSaldoDiarioService(ConsultaSaldoPort consulta, CacheSaldoPort cache) {
        this.consulta = Objects.requireNonNull(consulta, "consulta");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public SaldoDiarioConsulta consultar(UUID comercianteId, LocalDate data) {
        Objects.requireNonNull(comercianteId, "comercianteId");
        Objects.requireNonNull(data, "data");
        return cache.obterDia(comercianteId, data).orElseGet(() -> {
            MovimentoAcumulado acumulado = consulta.buscarDia(comercianteId, data);
            SaldoDiarioConsulta saldo = SaldoDiarioConsulta.de(acumulado.movimento(), acumulado.saldoAcumulado());
            cache.armazenarDia(comercianteId, data, saldo);
            return saldo;
        });
    }
}

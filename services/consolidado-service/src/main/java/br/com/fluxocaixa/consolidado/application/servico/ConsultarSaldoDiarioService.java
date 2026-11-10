package br.com.fluxocaixa.consolidado.application.servico;

import br.com.fluxocaixa.consolidado.application.consulta.SaldoDiarioConsulta;
import br.com.fluxocaixa.consolidado.application.port.in.ConsultarSaldoDiarioUseCase;
import br.com.fluxocaixa.consolidado.application.port.out.CacheSaldoPort;
import br.com.fluxocaixa.consolidado.application.port.out.ConsultaSaldoPort;
import br.com.fluxocaixa.consolidado.domain.model.MovimentoDia;

import java.math.BigDecimal;
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
            SaldoDiarioConsulta saldo = carregar(comercianteId, data);
            cache.armazenarDia(comercianteId, data, saldo);
            return saldo;
        });
    }

    private SaldoDiarioConsulta carregar(UUID comercianteId, LocalDate data) {
        MovimentoDia movimento = consulta.buscarDia(comercianteId, data).orElseGet(() -> MovimentoDia.vazio(data));
        BigDecimal acumulado = consulta.saldoAcumuladoAte(comercianteId, data);
        return SaldoDiarioConsulta.de(movimento, acumulado);
    }
}

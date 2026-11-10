package br.com.fluxocaixa.consolidado.application.servico;

import br.com.fluxocaixa.consolidado.application.comando.ComandoAplicarLancamento;
import br.com.fluxocaixa.consolidado.application.port.in.AplicarLancamentoUseCase;
import br.com.fluxocaixa.consolidado.application.port.out.AplicacaoLancamentoPort;
import br.com.fluxocaixa.consolidado.application.port.out.CacheSaldoPort;
import br.com.fluxocaixa.consolidado.application.resultado.ResultadoAplicacao;
import br.com.fluxocaixa.consolidado.domain.model.EfeitoLancamento;
import br.com.fluxocaixa.consolidado.domain.model.LancamentoConsolidado;
import br.com.fluxocaixa.consolidado.domain.model.SaldoDiario;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class AplicarLancamentoService implements AplicarLancamentoUseCase {

    private final AplicacaoLancamentoPort aplicacao;
    private final CacheSaldoPort cache;
    private final Clock relogio;

    public AplicarLancamentoService(AplicacaoLancamentoPort aplicacao, CacheSaldoPort cache, Clock relogio) {
        this.aplicacao = Objects.requireNonNull(aplicacao, "aplicacao");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.relogio = Objects.requireNonNull(relogio, "relogio");
    }

    @Override
    public ResultadoAplicacao executar(ComandoAplicarLancamento comando) {
        Objects.requireNonNull(comando, "comando");
        Instant agora = relogio.instant();
        LancamentoConsolidado lancamento = new LancamentoConsolidado(
                comando.comercianteId(),
                comando.dataLancamento(),
                comando.tipo(),
                comando.valor());
        SaldoDiario antes = SaldoDiario.novo(lancamento.comercianteId(), lancamento.data());
        SaldoDiario depois = antes.aplicar(lancamento, agora);
        EfeitoLancamento efeito = EfeitoLancamento.entre(antes, depois);
        boolean aplicado = aplicacao.aplicarSeNovo(
                comando.eventId(),
                lancamento.comercianteId(),
                lancamento.data(),
                efeito,
                agora);
        Duration atraso = atraso(comando.occurredAt(), agora);
        if (!aplicado) {
            return new ResultadoAplicacao.Duplicado(atraso);
        }
        cache.invalidar(comando.comercianteId());
        return new ResultadoAplicacao.Processado(atraso);
    }

    private static Duration atraso(Instant occurredAt, Instant agora) {
        Duration atraso = Duration.between(occurredAt, agora);
        if (atraso.isNegative()) {
            return Duration.ZERO;
        }
        return atraso;
    }
}

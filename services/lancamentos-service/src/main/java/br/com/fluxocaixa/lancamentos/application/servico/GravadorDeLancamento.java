package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.comando.RegistrarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.port.out.GeradorDeIds;
import br.com.fluxocaixa.lancamentos.application.port.out.IdempotenciaRepositorio;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepositorio;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicacaoOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.Relogio;
import br.com.fluxocaixa.lancamentos.application.resultado.RegistroIdempotencia;
import br.com.fluxocaixa.lancamentos.application.resultado.ResultadoEscrita;
import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GravadorDeLancamento {

    private final LancamentoRepositorio lancamentos;
    private final IdempotenciaRepositorio idempotencia;
    private final PublicacaoOutbox outbox;
    private final Relogio relogio;
    private final GeradorDeIds geradorDeIds;

    public GravadorDeLancamento(
            LancamentoRepositorio lancamentos,
            IdempotenciaRepositorio idempotencia,
            PublicacaoOutbox outbox,
            Relogio relogio,
            GeradorDeIds geradorDeIds
    ) {
        this.lancamentos = lancamentos;
        this.idempotencia = idempotencia;
        this.outbox = outbox;
        this.relogio = relogio;
        this.geradorDeIds = geradorDeIds;
    }

    @Transactional
    public ResultadoEscrita registrar(RegistrarLancamentoComando comando, String impressao) {
        Lancamento lancamento = Lancamento.registrar(
                geradorDeIds.novo(),
                comando.comercianteId(),
                comando.tipo(),
                new Dinheiro(comando.valor()),
                comando.dataLancamento(),
                comando.descricao(),
                comando.categoria(),
                relogio.agora()
        );
        lancamentos.salvar(lancamento);
        outbox.registrar(lancamento.paraEvento(geradorDeIds.novo()));
        if (comando.chaveIdempotencia() != null) {
            idempotencia.registrar(new RegistroIdempotencia(
                    comando.chaveIdempotencia(),
                    comando.comercianteId(),
                    lancamento.id(),
                    impressao
            ));
        }
        return new ResultadoEscrita(lancamento, false);
    }
}

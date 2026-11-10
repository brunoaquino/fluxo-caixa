package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.comando.EstornarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.excecao.LancamentoNaoEncontradoException;
import br.com.fluxocaixa.lancamentos.application.port.out.GeradorDeIds;
import br.com.fluxocaixa.lancamentos.application.port.out.IdempotenciaRepositorio;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepositorio;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicacaoOutbox;
import br.com.fluxocaixa.lancamentos.application.port.out.Relogio;
import br.com.fluxocaixa.lancamentos.application.resultado.RegistroIdempotencia;
import br.com.fluxocaixa.lancamentos.application.resultado.ResultadoEscrita;
import br.com.fluxocaixa.lancamentos.domain.excecao.EstornoDeEstornoNaoPermitidoException;
import br.com.fluxocaixa.lancamentos.domain.excecao.LancamentoJaEstornadoException;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GravadorDeEstorno {

    private final LancamentoRepositorio lancamentos;
    private final IdempotenciaRepositorio idempotencia;
    private final PublicacaoOutbox outbox;
    private final Relogio relogio;
    private final GeradorDeIds geradorDeIds;

    public GravadorDeEstorno(
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
    public ResultadoEscrita estornar(EstornarLancamentoComando comando, String impressao) {
        Lancamento original = lancamentos.buscarPorId(comando.lancamentoId(), comando.comercianteId())
                .orElseThrow(() -> new LancamentoNaoEncontradoException(comando.lancamentoId()));
        if (original.ehEstorno()) {
            throw new EstornoDeEstornoNaoPermitidoException(original.id());
        }
        if (lancamentos.existeEstornoPara(comando.comercianteId(), original.id())) {
            throw new LancamentoJaEstornadoException(original.id());
        }
        Lancamento estorno = original.estornar(geradorDeIds.novo(), comando.motivo(), relogio.agora());
        lancamentos.salvar(estorno);
        outbox.registrar(estorno.paraEvento(geradorDeIds.novo()));
        if (comando.chaveIdempotencia() != null) {
            idempotencia.registrar(new RegistroIdempotencia(
                    comando.chaveIdempotencia(),
                    comando.comercianteId(),
                    estorno.id(),
                    impressao
            ));
        }
        return new ResultadoEscrita(estorno, false);
    }
}

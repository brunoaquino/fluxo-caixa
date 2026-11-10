package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.excecao.IdempotenciaConflitanteException;
import br.com.fluxocaixa.lancamentos.application.excecao.LancamentoNaoEncontradoException;
import br.com.fluxocaixa.lancamentos.application.port.out.IdempotenciaRepositorio;
import br.com.fluxocaixa.lancamentos.application.port.out.LancamentoRepositorio;
import br.com.fluxocaixa.lancamentos.application.resultado.RegistroIdempotencia;
import br.com.fluxocaixa.lancamentos.application.resultado.ResultadoEscrita;
import br.com.fluxocaixa.lancamentos.domain.lancamento.Lancamento;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
public class ConsultaDeIdempotencia {

    private final IdempotenciaRepositorio idempotencia;
    private final LancamentoRepositorio lancamentos;

    public ConsultaDeIdempotencia(IdempotenciaRepositorio idempotencia, LancamentoRepositorio lancamentos) {
        this.idempotencia = idempotencia;
        this.lancamentos = lancamentos;
    }

    @Transactional(readOnly = true)
    public Optional<RegistroIdempotencia> buscar(UUID comercianteId, UUID chave) {
        return idempotencia.buscar(chave, comercianteId);
    }

    @Transactional(readOnly = true)
    public ResultadoEscrita reutilizar(UUID comercianteId, RegistroIdempotencia registro, String impressao) {
        if (!registro.impressaoDigital().equals(impressao)) {
            throw new IdempotenciaConflitanteException(registro.chave());
        }
        Lancamento lancamento = lancamentos.buscarPorId(registro.lancamentoId(), comercianteId)
                .orElseThrow(() -> new LancamentoNaoEncontradoException(registro.lancamentoId()));
        return new ResultadoEscrita(lancamento, true);
    }
}

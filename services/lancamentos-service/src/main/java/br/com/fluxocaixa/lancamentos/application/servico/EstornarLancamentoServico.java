package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.comando.EstornarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.excecao.IdempotenciaDuplicadaException;
import br.com.fluxocaixa.lancamentos.application.port.in.EstornarLancamento;
import br.com.fluxocaixa.lancamentos.application.resultado.ResultadoEscrita;
import br.com.fluxocaixa.lancamentos.domain.excecao.LancamentoJaEstornadoException;
import org.springframework.stereotype.Service;

@Service
public class EstornarLancamentoServico implements EstornarLancamento {

    private final GravadorDeEstorno gravador;
    private final ConsultaDeIdempotencia consulta;

    public EstornarLancamentoServico(GravadorDeEstorno gravador, ConsultaDeIdempotencia consulta) {
        this.gravador = gravador;
        this.consulta = consulta;
    }

    @Override
    public ResultadoEscrita executar(EstornarLancamentoComando comando) {
        String impressao = ImpressaoDigital.deEstorno(comando);
        if (comando.chaveIdempotencia() != null) {
            var existente = consulta.buscar(comando.comercianteId(), comando.chaveIdempotencia());
            if (existente.isPresent()) {
                return consulta.reutilizar(comando.comercianteId(), existente.get(), impressao);
            }
        }
        try {
            return gravador.estornar(comando, impressao);
        } catch (IdempotenciaDuplicadaException ex) {
            var existente = consulta.buscar(comando.comercianteId(), comando.chaveIdempotencia())
                    .orElseThrow(() -> ex);
            return consulta.reutilizar(comando.comercianteId(), existente, impressao);
        } catch (LancamentoJaEstornadoException ex) {
            if (comando.chaveIdempotencia() == null) {
                throw ex;
            }
            var existente = consulta.buscar(comando.comercianteId(), comando.chaveIdempotencia());
            if (existente.isEmpty()) {
                throw ex;
            }
            return consulta.reutilizar(comando.comercianteId(), existente.get(), impressao);
        }
    }
}

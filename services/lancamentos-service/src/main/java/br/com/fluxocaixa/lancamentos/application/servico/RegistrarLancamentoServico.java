package br.com.fluxocaixa.lancamentos.application.servico;

import br.com.fluxocaixa.lancamentos.application.comando.RegistrarLancamentoComando;
import br.com.fluxocaixa.lancamentos.application.excecao.IdempotenciaDuplicadaException;
import br.com.fluxocaixa.lancamentos.application.port.in.RegistrarLancamento;
import br.com.fluxocaixa.lancamentos.application.resultado.ResultadoEscrita;
import org.springframework.stereotype.Service;

@Service
public class RegistrarLancamentoServico implements RegistrarLancamento {

    private final GravadorDeLancamento gravador;
    private final ConsultaDeIdempotencia consulta;

    public RegistrarLancamentoServico(GravadorDeLancamento gravador, ConsultaDeIdempotencia consulta) {
        this.gravador = gravador;
        this.consulta = consulta;
    }

    @Override
    public ResultadoEscrita executar(RegistrarLancamentoComando comando) {
        String impressao = ImpressaoDigital.deRegistro(comando);
        if (comando.chaveIdempotencia() != null) {
            var existente = consulta.buscar(comando.comercianteId(), comando.chaveIdempotencia());
            if (existente.isPresent()) {
                return consulta.reutilizar(comando.comercianteId(), existente.get(), impressao);
            }
        }
        try {
            return gravador.registrar(comando, impressao);
        } catch (IdempotenciaDuplicadaException ex) {
            var existente = consulta.buscar(comando.comercianteId(), comando.chaveIdempotencia())
                    .orElseThrow(() -> ex);
            return consulta.reutilizar(comando.comercianteId(), existente, impressao);
        }
    }
}

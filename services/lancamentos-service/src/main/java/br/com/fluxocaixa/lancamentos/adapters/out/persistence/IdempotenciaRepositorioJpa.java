package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import br.com.fluxocaixa.lancamentos.application.excecao.IdempotenciaDuplicadaException;
import br.com.fluxocaixa.lancamentos.application.port.out.IdempotenciaRepositorio;
import br.com.fluxocaixa.lancamentos.application.port.out.Relogio;
import br.com.fluxocaixa.lancamentos.application.resultado.RegistroIdempotencia;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class IdempotenciaRepositorioJpa implements IdempotenciaRepositorio {

    private final IdempotenciaSpringData springData;
    private final Relogio relogio;

    public IdempotenciaRepositorioJpa(IdempotenciaSpringData springData, Relogio relogio) {
        this.springData = springData;
        this.relogio = relogio;
    }

    @Override
    public Optional<RegistroIdempotencia> buscar(UUID chave, UUID comercianteId) {
        return springData.findById(new IdempotenciaId(chave, comercianteId))
                .map(entidade -> new RegistroIdempotencia(
                        entidade.getId().getChave(),
                        entidade.getId().getComercianteId(),
                        entidade.getLancamentoId(),
                        entidade.getImpressaoDigital()
                ));
    }

    @Override
    public void registrar(RegistroIdempotencia registro) {
        var entidade = new IdempotenciaJpa(
                new IdempotenciaId(registro.chave(), registro.comercianteId()),
                registro.lancamentoId(),
                registro.impressaoDigital(),
                relogio.agora()
        );
        try {
            springData.saveAndFlush(entidade);
        } catch (DataIntegrityViolationException ex) {
            throw new IdempotenciaDuplicadaException(registro.chave());
        }
    }
}

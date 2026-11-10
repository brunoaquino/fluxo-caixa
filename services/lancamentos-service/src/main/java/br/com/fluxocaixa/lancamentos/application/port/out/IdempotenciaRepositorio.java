package br.com.fluxocaixa.lancamentos.application.port.out;

import br.com.fluxocaixa.lancamentos.application.resultado.RegistroIdempotencia;

import java.util.Optional;
import java.util.UUID;

public interface IdempotenciaRepositorio {

    Optional<RegistroIdempotencia> buscar(UUID chave, UUID comercianteId);

    void registrar(RegistroIdempotencia registro);
}

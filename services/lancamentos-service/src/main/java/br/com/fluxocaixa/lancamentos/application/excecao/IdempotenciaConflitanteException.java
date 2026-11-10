package br.com.fluxocaixa.lancamentos.application.excecao;

import java.util.UUID;

public class IdempotenciaConflitanteException extends RuntimeException {

    public IdempotenciaConflitanteException(UUID chave) {
        super("A chave de idempotência " + chave + " já foi usada com outro conteúdo");
    }
}

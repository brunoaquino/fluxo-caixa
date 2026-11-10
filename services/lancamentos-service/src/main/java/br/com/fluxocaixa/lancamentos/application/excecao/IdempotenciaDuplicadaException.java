package br.com.fluxocaixa.lancamentos.application.excecao;

import java.util.UUID;

/**
 * Outra transação gravou a mesma chave antes do commit desta.
 * A transação corrente deve sofrer rollback e a chamada deve reler o registro vencedor.
 */
public class IdempotenciaDuplicadaException extends RuntimeException {

    public IdempotenciaDuplicadaException(UUID chave) {
        super("Chave de idempotência já registrada: " + chave);
    }
}

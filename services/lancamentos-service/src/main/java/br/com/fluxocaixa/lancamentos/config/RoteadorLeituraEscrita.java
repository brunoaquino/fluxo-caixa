package br.com.fluxocaixa.lancamentos.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Manda transação somente leitura para a réplica. Sem o marcador, cai no
 * flag do Spring, que só fica visível depois que a conexão lazy é pedida.
 */
class RoteadorLeituraEscrita extends AbstractRoutingDataSource {

    static final String ESCRITA = "escrita";
    static final String LEITURA = "leitura";

    private final MarcadorDeLeitura marcador;

    RoteadorLeituraEscrita(MarcadorDeLeitura marcador) {
        this.marcador = marcador;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        Boolean marcado = marcador.somenteLeitura();
        if (marcado != null) {
            return marcado ? LEITURA : ESCRITA;
        }
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? LEITURA : ESCRITA;
    }
}

package br.com.fluxocaixa.consolidado.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class RoteadorLeituraEscrita extends AbstractRoutingDataSource {

    public static final String ESCRITA = "escrita";
    public static final String LEITURA = "leitura";

    @Override
    protected Object determineCurrentLookupKey() {
        return chaveAtual();
    }

    public static String chaveAtual() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? LEITURA : ESCRITA;
    }
}

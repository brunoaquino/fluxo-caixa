package br.com.fluxocaixa.lancamentos.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoteadorLeituraEscritaTest {

    private final MarcadorDeLeitura marcador = new MarcadorDeLeitura();
    private final DataSource escrita = mock(DataSource.class);
    private final DataSource leitura = mock(DataSource.class);

    @AfterEach
    void limpa() {
        marcador.limpar();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void transacaoSomenteLeituraUsaReplica() throws Exception {
        when(leitura.getConnection()).thenReturn(mock(Connection.class));
        RoteadorLeituraEscrita roteador = roteador();
        marcador.marcar(true);

        new LazyConnectionDataSourceProxy(roteador).getConnection().createStatement();

        verify(leitura, atLeastOnce()).getConnection();
        verifyNoInteractions(escrita);
    }

    @Test
    void transacaoDeEscritaUsaPrimario() throws Exception {
        when(escrita.getConnection()).thenReturn(mock(Connection.class));
        RoteadorLeituraEscrita roteador = roteador();
        marcador.marcar(false);

        assertThat(roteador.getConnection()).isNotNull();

        verify(escrita).getConnection();
        verifyNoInteractions(leitura);
    }

    @Test
    void semMarcadorCaiNoFlagDaTransacao() throws Exception {
        when(leitura.getConnection()).thenReturn(mock(Connection.class));
        RoteadorLeituraEscrita roteador = roteador();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        roteador.getConnection();

        verify(leitura).getConnection();
    }

    private RoteadorLeituraEscrita roteador() {
        RoteadorLeituraEscrita roteador = new RoteadorLeituraEscrita(marcador);
        roteador.setDefaultTargetDataSource(escrita);
        roteador.setTargetDataSources(Map.of(
                RoteadorLeituraEscrita.ESCRITA, escrita,
                RoteadorLeituraEscrita.LEITURA, leitura
        ));
        roteador.afterPropertiesSet();
        return roteador;
    }
}

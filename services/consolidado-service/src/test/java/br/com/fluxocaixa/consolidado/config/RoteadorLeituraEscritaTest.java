package br.com.fluxocaixa.consolidado.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class RoteadorLeituraEscritaTest {

    @AfterEach
    void limpa() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void semTransacaoSomenteLeituraVaiParaEscrita() {
        assertThat(RoteadorLeituraEscrita.chaveAtual()).isEqualTo(RoteadorLeituraEscrita.ESCRITA);
    }

    @Test
    void transacaoSomenteLeituraVaiParaReplica() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThat(RoteadorLeituraEscrita.chaveAtual()).isEqualTo(RoteadorLeituraEscrita.LEITURA);
    }
}

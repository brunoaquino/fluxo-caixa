package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RelayOutbox {

    private final ProcessadorDeOutbox processador;

    public RelayOutbox(ProcessadorDeOutbox processador) {
        this.processador = processador;
    }

    @Scheduled(fixedDelayString = "${app.outbox.intervalo-ms:500}")
    public void publicarPendentes() {
        processador.processarLote();
    }
}

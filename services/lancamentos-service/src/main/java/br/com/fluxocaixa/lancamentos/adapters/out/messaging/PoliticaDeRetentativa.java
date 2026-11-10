package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import java.time.Instant;

final class PoliticaDeRetentativa {

    private PoliticaDeRetentativa() {
    }

    static Instant proxima(int tentativasAtuais, Instant agora, int maximoSegundos) {
        int novaTentativa = tentativasAtuais + 1;
        long deslocamento = Math.min(novaTentativa, 6);
        long segundos = Math.min(maximoSegundos, 1L << deslocamento);
        return agora.plusSeconds(Math.max(1, segundos));
    }
}

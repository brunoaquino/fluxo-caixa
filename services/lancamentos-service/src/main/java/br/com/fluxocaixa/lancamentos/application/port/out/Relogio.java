package br.com.fluxocaixa.lancamentos.application.port.out;

import java.time.Instant;

public interface Relogio {

    Instant agora();
}

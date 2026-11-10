package br.com.fluxocaixa.consolidado.application.resultado;

import java.time.Duration;

public sealed interface ResultadoAplicacao permits ResultadoAplicacao.Processado, ResultadoAplicacao.Duplicado {

    Duration atraso();

    record Processado(Duration atraso) implements ResultadoAplicacao {
    }

    record Duplicado(Duration atraso) implements ResultadoAplicacao {
    }
}

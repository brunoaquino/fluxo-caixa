package br.com.fluxocaixa.lancamentos.application.port.out;

import java.util.UUID;

public interface GeradorDeIds {

    UUID novo();
}

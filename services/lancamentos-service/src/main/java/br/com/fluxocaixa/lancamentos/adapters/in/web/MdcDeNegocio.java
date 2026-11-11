package br.com.fluxocaixa.lancamentos.adapters.in.web;

import org.slf4j.MDC;

import java.util.UUID;

public final class MdcDeNegocio {

    public static final String COMERCIANTE = "comercianteId";
    public static final String LANCAMENTO = "lancamentoId";
    public static final String EVENTO = "eventId";

    private MdcDeNegocio() {
    }

    public static void comerciante(UUID id) {
        if (id != null) {
            MDC.put(COMERCIANTE, id.toString());
        }
    }

    public static void lancamento(UUID id) {
        if (id != null) {
            MDC.put(LANCAMENTO, id.toString());
        }
    }

    public static void limpar() {
        MDC.remove(COMERCIANTE);
        MDC.remove(LANCAMENTO);
        MDC.remove(EVENTO);
    }
}

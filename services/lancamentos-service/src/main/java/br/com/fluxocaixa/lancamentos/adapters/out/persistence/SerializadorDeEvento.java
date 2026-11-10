package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import br.com.fluxocaixa.lancamentos.domain.dinheiro.Dinheiro;
import br.com.fluxocaixa.lancamentos.domain.lancamento.LancamentoRegistrado;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.format.DateTimeFormatter;

final class SerializadorDeEvento {

    private static final DateTimeFormatter INSTANTE = DateTimeFormatter.ISO_INSTANT;

    private final JsonMapper jsonMapper;

    SerializadorDeEvento(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    String serializar(LancamentoRegistrado evento) {
        ObjectNode no = jsonMapper.createObjectNode();
        no.put("eventId", evento.eventId().toString());
        no.put("eventType", evento.eventType());
        no.put("eventVersion", evento.eventVersion());
        no.put("occurredAt", INSTANTE.format(evento.occurredAt()));
        no.put("lancamentoId", evento.lancamentoId().toString());
        no.put("comercianteId", evento.comercianteId().toString());
        no.put("tipo", evento.tipo().name());
        no.put("valor", evento.valor().formatado());
        no.put("moeda", Dinheiro.MOEDA);
        no.put("dataLancamento", evento.dataLancamento().toString());
        if (evento.lancamentoEstornadoId() == null) {
            no.putNull("lancamentoEstornadoId");
        } else {
            no.put("lancamentoEstornadoId", evento.lancamentoEstornadoId().toString());
        }
        return jsonMapper.writeValueAsString(no);
    }
}

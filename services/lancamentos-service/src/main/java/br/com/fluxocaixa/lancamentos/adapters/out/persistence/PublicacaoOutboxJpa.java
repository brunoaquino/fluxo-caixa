package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import br.com.fluxocaixa.lancamentos.application.port.out.PublicacaoOutbox;
import br.com.fluxocaixa.lancamentos.domain.lancamento.LancamentoRegistrado;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class PublicacaoOutboxJpa implements PublicacaoOutbox {

    private final OutboxEventoSpringData springData;
    private final SerializadorDeEvento serializador;

    public PublicacaoOutboxJpa(OutboxEventoSpringData springData, JsonMapper jsonMapper) {
        this.springData = springData;
        this.serializador = new SerializadorDeEvento(jsonMapper);
    }

    @Override
    public void registrar(LancamentoRegistrado evento) {
        springData.save(new OutboxEventoJpa(
                evento.eventId(),
                evento.lancamentoId(),
                evento.eventType(),
                serializador.serializar(evento),
                evento.occurredAt(),
                0,
                evento.occurredAt()
        ));
    }
}

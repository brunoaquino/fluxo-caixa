package br.com.fluxocaixa.lancamentos.adapters.out.persistence;

import br.com.fluxocaixa.lancamentos.adapters.in.web.FiltroCorrelacao;
import br.com.fluxocaixa.lancamentos.application.port.out.PublicacaoOutbox;
import br.com.fluxocaixa.lancamentos.domain.lancamento.LancamentoRegistrado;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.slf4j.MDC;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class PublicacaoOutboxJpa implements PublicacaoOutbox {

    private final OutboxEventoSpringData springData;
    private final SerializadorDeEvento serializador;
    private final Tracer tracer;
    private final Propagator propagator;

    public PublicacaoOutboxJpa(
            OutboxEventoSpringData springData,
            JsonMapper jsonMapper,
            Tracer tracer,
            Propagator propagator
    ) {
        this.springData = springData;
        this.serializador = new SerializadorDeEvento(jsonMapper);
        this.tracer = tracer;
        this.propagator = propagator;
    }

    @Override
    public void registrar(LancamentoRegistrado evento) {
        Map<String, String> carrier = new LinkedHashMap<>();
        Span span = tracer.currentSpan();
        if (span != null) {
            propagator.inject(span.context(), carrier, (mapa, chave, valor) -> mapa.put(chave, valor));
        }
        springData.save(new OutboxEventoJpa(
                evento.eventId(),
                evento.lancamentoId(),
                evento.eventType(),
                serializador.serializar(evento),
                evento.occurredAt(),
                0,
                evento.occurredAt(),
                carrier.get("traceparent"),
                carrier.get("tracestate"),
                MDC.get(FiltroCorrelacao.MDC_CORRELACAO)
        ));
    }
}

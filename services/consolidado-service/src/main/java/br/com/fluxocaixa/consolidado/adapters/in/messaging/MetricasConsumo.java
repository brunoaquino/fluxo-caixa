package br.com.fluxocaixa.consolidado.adapters.in.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MetricasConsumo {

    private final Counter processados;
    private final Counter duplicados;
    private final Counter falhas;
    private final Timer atraso;

    public MetricasConsumo(MeterRegistry registry) {
        this.processados = Counter.builder("consolidado.eventos.processados")
                .description("Eventos de lançamento aplicados na projeção")
                .register(registry);
        this.duplicados = Counter.builder("consolidado.eventos.duplicados")
                .description("Eventos ignorados porque o eventId já foi processado")
                .register(registry);
        this.falhas = Counter.builder("consolidado.eventos.falhas")
                .description("Falhas de processamento, incluindo mensagens poison")
                .register(registry);
        this.atraso = Timer.builder("consolidado.eventos.lag")
                .description("Atraso entre occurredAt e o processamento do evento")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void processado(Duration atrasoEvento) {
        processados.increment();
        registrarAtraso(atrasoEvento);
    }

    public void duplicado(Duration atrasoEvento) {
        duplicados.increment();
        registrarAtraso(atrasoEvento);
    }

    public void falha() {
        falhas.increment();
    }

    private void registrarAtraso(Duration atrasoEvento) {
        Duration medido = atrasoEvento == null || atrasoEvento.isNegative() ? Duration.ZERO : atrasoEvento;
        atraso.record(medido);
    }
}

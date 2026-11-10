package br.com.fluxocaixa.consolidado.adapters.in.messaging;

import br.com.fluxocaixa.consolidado.application.port.in.AplicarLancamentoUseCase;
import br.com.fluxocaixa.consolidado.application.resultado.ResultadoAplicacao;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessadorEventoLancamentoTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MetricasConsumo metricas = new MetricasConsumo(registry);
    private final AtomicInteger apagadas = new AtomicInteger();
    private final TradutorEventoLancamento tradutor = new TradutorEventoLancamento(JsonMapper.builder().build());

    @Test
    void apagaMensagemSomenteDepoisDoSucessoOuDaDuplicata() {
        ProcessadorEventoLancamento processador = processador(comando -> new ResultadoAplicacao.Processado(Duration.ofMillis(10)));
        assertThat(processador.consumir(mensagem(eventoValido()))).isEqualTo(DesfechoConsumo.PROCESSADO);
        assertThat(apagadas).hasValue(1);

        ProcessadorEventoLancamento duplicado = processador(comando -> new ResultadoAplicacao.Duplicado(Duration.ZERO));
        assertThat(duplicado.consumir(mensagem(eventoValido()))).isEqualTo(DesfechoConsumo.DUPLICADO);
        assertThat(apagadas).hasValue(2);
    }

    @Test
    void poisonEFalhaNaoApagamAMensagem() {
        ProcessadorEventoLancamento poison = processador(comando -> {
            throw new AssertionError("não deveria aplicar");
        });
        assertThat(poison.consumir(mensagem("{"))).isEqualTo(DesfechoConsumo.POISON);

        ProcessadorEventoLancamento falha = processador(comando -> {
            throw new IllegalStateException("banco fora");
        });
        assertThat(falha.consumir(mensagem(eventoValido()))).isEqualTo(DesfechoConsumo.FALHA);
        assertThat(apagadas).hasValue(0);
        assertThat(registry.counter("consolidado.eventos.falhas").count()).isEqualTo(2.0);
    }

    @Test
    void falhaAoApagarNaoDesfazOProcessamento() {
        ProcessadorEventoLancamento processador = new ProcessadorEventoLancamento(
                tradutor,
                comando -> new ResultadoAplicacao.Processado(Duration.ZERO),
                metricas,
                receipt -> {
                    throw new IllegalStateException("sqs fora");
                });

        assertThat(processador.consumir(mensagem(eventoValido()))).isEqualTo(DesfechoConsumo.PROCESSADO);
        assertThat(registry.counter("consolidado.eventos.falhas").count()).isEqualTo(1.0);
        assertThat(registry.counter("consolidado.eventos.processados").count()).isEqualTo(1.0);
    }

    private ProcessadorEventoLancamento processador(AplicarLancamentoUseCase casoDeUso) {
        return new ProcessadorEventoLancamento(tradutor, casoDeUso, metricas, receipt -> apagadas.incrementAndGet());
    }

    private static MensagemRecebida mensagem(String corpo) {
        return new MensagemRecebida(corpo, Map.of(), "receipt-1");
    }

    private static String eventoValido() {
        return """
                {
                  "eventId": "0b8f6c1e-6d1a-4b8e-9a43-0f1b7e2f9c11",
                  "eventType": "LancamentoRegistrado",
                  "eventVersion": 1,
                  "occurredAt": "2026-11-10T18:00:00Z",
                  "lancamentoId": "5f0e1c7a-1d2b-4c3d-8e9f-0a1b2c3d4e5f",
                  "comercianteId": "11111111-1111-1111-1111-111111111111",
                  "tipo": "CREDITO",
                  "valor": "10.00",
                  "moeda": "BRL",
                  "dataLancamento": "2026-11-10",
                  "lancamentoEstornadoId": null
                }
                """;
    }
}

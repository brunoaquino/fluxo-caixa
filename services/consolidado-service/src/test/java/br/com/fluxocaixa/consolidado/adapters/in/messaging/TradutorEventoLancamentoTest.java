package br.com.fluxocaixa.consolidado.adapters.in.messaging;

import br.com.fluxocaixa.consolidado.application.comando.ComandoAplicarLancamento;
import br.com.fluxocaixa.consolidado.domain.model.TipoLancamento;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradutorEventoLancamentoTest {

    private final TradutorEventoLancamento tradutor = new TradutorEventoLancamento(JsonMapper.builder().build());

    @Test
    void traduzEventoDoContrato() {
        ComandoAplicarLancamento comando = tradutor.traduzir(evento("CREDITO", "150.00", null), Map.of("eventType", "LancamentoRegistrado"));

        assertThat(comando.eventId()).isEqualTo(UUID.fromString("0b8f6c1e-6d1a-4b8e-9a43-0f1b7e2f9c11"));
        assertThat(comando.tipo()).isEqualTo(TipoLancamento.CREDITO);
        assertThat(comando.valor().quantia()).isEqualByComparingTo("150.00");
        assertThat(comando.lancamentoEstornadoId()).isNull();
        assertThat(comando.dataLancamento()).hasToString("2026-11-10");
    }

    @Test
    void traduzEstornoSemTratamentoEspecial() {
        UUID estorno = UUID.randomUUID();
        ComandoAplicarLancamento comando = tradutor.traduzir(evento("DEBITO", "150.00", estorno), Map.of());

        assertThat(comando.tipo()).isEqualTo(TipoLancamento.DEBITO);
        assertThat(comando.lancamentoEstornadoId()).isEqualTo(estorno);
    }

    @Test
    void mensagemInvalidaEhPoison() {
        assertThatThrownBy(() -> tradutor.traduzir("{", Map.of())).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> tradutor.traduzir("[]", Map.of())).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> tradutor.traduzir(evento("CREDITO", "150.00", null).replace("\"eventVersion\": 1", "\"eventVersion\": 2"), Map.of()))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> tradutor.traduzir(evento("PIX", "150.00", null), Map.of()))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> tradutor.traduzir(evento("CREDITO", "150.00", null), Map.of("eventType", "OutroEvento")))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> tradutor.traduzir(evento("CREDITO", "0.00", null), Map.of()))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> tradutor.traduzir(evento("CREDITO", "150.00", null).replace("BRL", "USD"), Map.of()))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> tradutor.traduzir("x".repeat(TradutorEventoLancamento.TAMANHO_MAXIMO_CORPO + 1), Map.of()))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> tradutor.traduzir(evento("CREDITO", "150.00", null), Map.of("eventType", "x".repeat(600))))
                .isInstanceOf(MensagemInvalidaException.class);
    }

    private static String evento(String tipo, String valor, UUID estorno) {
        String estornoJson = estorno == null ? "null" : "\"" + estorno + "\"";
        return """
                {
                  "eventId": "0b8f6c1e-6d1a-4b8e-9a43-0f1b7e2f9c11",
                  "eventType": "LancamentoRegistrado",
                  "eventVersion": 1,
                  "occurredAt": "2026-11-10T18:00:00.123Z",
                  "lancamentoId": "5f0e1c7a-1d2b-4c3d-8e9f-0a1b2c3d4e5f",
                  "comercianteId": "11111111-1111-1111-1111-111111111111",
                  "tipo": "%s",
                  "valor": "%s",
                  "moeda": "BRL",
                  "dataLancamento": "2026-11-10",
                  "lancamentoEstornadoId": %s
                }
                """.formatted(tipo, valor, estornoJson);
    }
}

package br.com.fluxocaixa.consolidado.adapters.in.messaging;

import br.com.fluxocaixa.consolidado.application.comando.ComandoAplicarLancamento;
import br.com.fluxocaixa.consolidado.domain.exception.DinheiroInvalidoException;
import br.com.fluxocaixa.consolidado.domain.model.Dinheiro;
import br.com.fluxocaixa.consolidado.domain.model.TipoLancamento;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

/**
 * Camada anticorrupção: o JSON publicado por Lançamentos vira um comando do consolidado.
 * Campo desconhecido é ignorado. eventType ou eventVersion inesperados tornam a mensagem poison.
 */
@Component
public class TradutorEventoLancamento {

    static final String EVENTO = "LancamentoRegistrado";
    static final int VERSAO = 1;

    private final JsonMapper json;

    public TradutorEventoLancamento(JsonMapper json) {
        this.json = json;
    }

    public ComandoAplicarLancamento traduzir(String corpo, Map<String, String> atributos) {
        try {
            validarAtributo(atributos);
            JsonNode raiz = json.readTree(corpo == null ? "" : corpo);
            if (raiz == null || !raiz.isObject()) {
                throw new MensagemInvalidaException("Corpo do evento não é um objeto JSON");
            }
            String tipoEvento = textoObrigatorio(raiz, "eventType");
            if (!EVENTO.equals(tipoEvento)) {
                throw new MensagemInvalidaException("eventType não suportado: " + tipoEvento);
            }
            int versao = inteiroObrigatorio(raiz, "eventVersion");
            if (versao != VERSAO) {
                throw new MensagemInvalidaException("eventVersion não suportada: " + versao);
            }
            String moeda = textoObrigatorio(raiz, "moeda");
            if (!Dinheiro.MOEDA.equals(moeda)) {
                throw new MensagemInvalidaException("Moeda não suportada: " + moeda);
            }
            return new ComandoAplicarLancamento(
                    uuid(textoObrigatorio(raiz, "eventId"), "eventId"),
                    uuid(textoObrigatorio(raiz, "lancamentoId"), "lancamentoId"),
                    uuid(textoObrigatorio(raiz, "comercianteId"), "comercianteId"),
                    tipo(textoObrigatorio(raiz, "tipo")),
                    Dinheiro.de(textoObrigatorio(raiz, "valor")),
                    data(textoObrigatorio(raiz, "dataLancamento")),
                    instante(textoObrigatorio(raiz, "occurredAt")),
                    estorno(raiz.get("lancamentoEstornadoId")));
        } catch (MensagemInvalidaException ex) {
            throw ex;
        } catch (DinheiroInvalidoException | DateTimeParseException | JacksonException ex) {
            throw new MensagemInvalidaException("Evento de lançamento inválido", ex);
        }
    }

    private static void validarAtributo(Map<String, String> atributos) {
        if (atributos == null || atributos.isEmpty()) {
            return;
        }
        String tipo = atributos.get("eventType");
        if (tipo != null && !EVENTO.equals(tipo)) {
            throw new MensagemInvalidaException("Atributo eventType não suportado: " + tipo);
        }
    }

    private static String textoObrigatorio(JsonNode raiz, String campo) {
        JsonNode no = raiz.get(campo);
        if (no == null || no.isNull() || !no.isTextual() || no.asText().isBlank()) {
            throw new MensagemInvalidaException("Campo obrigatório ausente ou inválido: " + campo);
        }
        return no.asText();
    }

    private static int inteiroObrigatorio(JsonNode raiz, String campo) {
        JsonNode no = raiz.get(campo);
        if (no == null || no.isNull() || !no.isNumber() || !no.isIntegralNumber() || no.intValue() != no.numberValue().intValue()) {
            throw new MensagemInvalidaException("Campo numérico inteiro obrigatório: " + campo);
        }
        return no.intValue();
    }

    private static UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException ex) {
            throw new MensagemInvalidaException("UUID inválido em " + campo, ex);
        }
    }

    private static TipoLancamento tipo(String valor) {
        return switch (valor) {
            case "CREDITO" -> TipoLancamento.CREDITO;
            case "DEBITO" -> TipoLancamento.DEBITO;
            default -> throw new MensagemInvalidaException("Tipo de lançamento não suportado: " + valor);
        };
    }

    private static LocalDate data(String valor) {
        return LocalDate.parse(valor);
    }

    private static Instant instante(String valor) {
        return Instant.parse(valor);
    }

    private static UUID estorno(JsonNode no) {
        if (no == null || no.isNull() || no.isMissingNode()) {
            return null;
        }
        if (!no.isTextual() || no.asText().isBlank()) {
            throw new MensagemInvalidaException("lancamentoEstornadoId inválido");
        }
        return uuid(no.asText(), "lancamentoEstornadoId");
    }
}

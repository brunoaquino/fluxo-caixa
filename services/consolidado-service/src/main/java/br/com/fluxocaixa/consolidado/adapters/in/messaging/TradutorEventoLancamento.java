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
import java.util.Set;
import java.util.UUID;

/**
 * Camada anticorrupção: o JSON publicado por Lançamentos vira um comando do consolidado.
 * Campo desconhecido é ignorado. eventType ou eventVersion inesperados tornam a mensagem poison.
 */
@Component
public class TradutorEventoLancamento {

    static final String EVENTO = "LancamentoRegistrado";
    static final int VERSAO = 1;
    static final int TAMANHO_MAXIMO_CORPO = 8_192;
    static final int MAXIMO_CAMPOS = 32;
    static final int MAXIMO_ATRIBUTOS = 16;
    static final int TAMANHO_MAXIMO_ATRIBUTO = 512;

    private static final Set<String> CAMPOS = Set.of(
            "eventId",
            "eventType",
            "eventVersion",
            "occurredAt",
            "lancamentoId",
            "comercianteId",
            "tipo",
            "valor",
            "moeda",
            "dataLancamento",
            "lancamentoEstornadoId");

    private final JsonMapper json;

    public TradutorEventoLancamento(JsonMapper json) {
        this.json = json;
    }

    public ComandoAplicarLancamento traduzir(String corpo, Map<String, String> atributos) {
        try {
            validarAtributos(atributos);
            if (corpo == null || corpo.isBlank()) {
                throw new MensagemInvalidaException("Corpo do evento ausente");
            }
            if (corpo.length() > TAMANHO_MAXIMO_CORPO) {
                throw new MensagemInvalidaException("Corpo do evento acima do limite");
            }
            JsonNode raiz = json.readTree(corpo);
            if (raiz == null || !raiz.isObject()) {
                throw new MensagemInvalidaException("Corpo do evento não é um objeto JSON");
            }
            if (raiz.size() > MAXIMO_CAMPOS) {
                throw new MensagemInvalidaException("Quantidade de campos acima do limite");
            }
            rejeitarCampoLongo(raiz);
            String tipoEvento = textoObrigatorio(raiz, "eventType");
            if (!EVENTO.equals(tipoEvento)) {
                throw new MensagemInvalidaException("eventType não suportado");
            }
            int versao = inteiroObrigatorio(raiz, "eventVersion");
            if (versao != VERSAO) {
                throw new MensagemInvalidaException("eventVersion não suportada");
            }
            String moeda = textoObrigatorio(raiz, "moeda");
            if (!Dinheiro.MOEDA.equals(moeda)) {
                throw new MensagemInvalidaException("Moeda não suportada");
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

    private static void validarAtributos(Map<String, String> atributos) {
        if (atributos == null || atributos.isEmpty()) {
            return;
        }
        if (atributos.size() > MAXIMO_ATRIBUTOS) {
            throw new MensagemInvalidaException("Quantidade de atributos acima do limite");
        }
        for (Map.Entry<String, String> entrada : atributos.entrySet()) {
            String chave = entrada.getKey() == null ? "" : entrada.getKey();
            String valor = entrada.getValue() == null ? "" : entrada.getValue();
            if (chave.length() > 64 || valor.length() > TAMANHO_MAXIMO_ATRIBUTO) {
                throw new MensagemInvalidaException("Atributo fora do limite de tamanho");
            }
        }
        String tipo = atributos.get("eventType");
        if (tipo != null && !EVENTO.equals(tipo)) {
            throw new MensagemInvalidaException("Atributo eventType não suportado");
        }
    }

    private static void rejeitarCampoLongo(JsonNode raiz) {
        for (Map.Entry<String, JsonNode> campo : raiz.properties()) {
            JsonNode valor = campo.getValue();
            if (valor == null || valor.isNull()) {
                continue;
            }
            if (!CAMPOS.contains(campo.getKey()) && valor.isContainer()) {
                throw new MensagemInvalidaException("Campo aninhado não suportado");
            }
            if (valor.isTextual() && valor.asText().length() > 64) {
                throw new MensagemInvalidaException("Campo textual acima do limite");
            }
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
            default -> throw new MensagemInvalidaException("Tipo de lançamento não suportado");
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

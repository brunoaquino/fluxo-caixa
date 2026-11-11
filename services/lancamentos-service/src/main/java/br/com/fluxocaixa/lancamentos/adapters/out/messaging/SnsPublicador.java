package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import br.com.fluxocaixa.lancamentos.config.PropriedadesMensageria;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;

@Component
public class SnsPublicador {

    public static final String INSTANCIA = "publicacaoSns";

    private final EnvioSns envio;
    private final String topicArn;

    public SnsPublicador(EnvioSns envio, PropriedadesMensageria propriedades) {
        if (propriedades.topicArn() == null || propriedades.topicArn().isBlank()) {
            throw new IllegalStateException("LANCAMENTOS_TOPIC_ARN é obrigatório");
        }
        this.envio = envio;
        this.topicArn = propriedades.topicArn();
    }

    @CircuitBreaker(name = INSTANCIA)
    @Retry(name = INSTANCIA)
    public void publicar(String payload, String eventType, AtributosDeRastreio rastreio) {
        PublishRequest requisicao = PublishRequest.builder()
                .topicArn(topicArn)
                .message(payload)
                .messageAttributes(atributos(eventType, rastreio))
                .build();
        try {
            envio.enviar(requisicao).toCompletableFuture().join();
        } catch (CompletionException ex) {
            throw propagar(ex.getCause() == null ? ex : ex.getCause());
        }
    }

    private static RuntimeException propagar(Throwable causa) {
        if (causa instanceof TimeoutException || causa instanceof CancellationException) {
            return new TimeoutDePublicacaoException(causa);
        }
        if (causa instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException(causa);
    }

    private static Map<String, MessageAttributeValue> atributos(String eventType, AtributosDeRastreio rastreio) {
        Map<String, MessageAttributeValue> atributos = new LinkedHashMap<>();
        atributos.put("eventType", texto(eventType));
        if (rastreio != null) {
            adicionar(atributos, "traceparent", rastreio.traceParent());
            adicionar(atributos, "tracestate", rastreio.traceState());
            adicionar(atributos, "correlationId", rastreio.correlationId());
        }
        return atributos;
    }

    private static void adicionar(Map<String, MessageAttributeValue> atributos, String nome, String valor) {
        if (valor != null && !valor.isBlank()) {
            atributos.put(nome, texto(valor));
        }
    }

    private static MessageAttributeValue texto(String valor) {
        return MessageAttributeValue.builder()
                .dataType("String")
                .stringValue(valor)
                .build();
    }
}

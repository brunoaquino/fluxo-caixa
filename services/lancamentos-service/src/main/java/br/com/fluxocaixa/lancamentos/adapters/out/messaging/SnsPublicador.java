package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import br.com.fluxocaixa.lancamentos.config.PropriedadesMensageria;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

@Component
public class SnsPublicador {

    private final SnsClient snsClient;
    private final String topicArn;

    public SnsPublicador(SnsClient snsClient, PropriedadesMensageria propriedades) {
        if (propriedades.topicArn() == null || propriedades.topicArn().isBlank()) {
            throw new IllegalStateException("LANCAMENTOS_TOPIC_ARN é obrigatório");
        }
        this.snsClient = snsClient;
        this.topicArn = propriedades.topicArn();
    }

    public void publicar(String payload, String eventType) {
        snsClient.publish(PublishRequest.builder()
                .topicArn(topicArn)
                .message(payload)
                .messageAttributes(Map.of(
                        "eventType",
                        MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(eventType)
                                .build()
                ))
                .build());
    }
}

package br.com.fluxocaixa.lancamentos.adapters.out.messaging;

import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Component
public class EnvioSns {

    private final SnsClient snsClient;
    private final ExecutorService executor;

    public EnvioSns(SnsClient snsClient, ExecutorService publicacaoSnsExecutor) {
        this.snsClient = snsClient;
        this.executor = publicacaoSnsExecutor;
    }

    @TimeLimiter(name = SnsPublicador.INSTANCIA)
    public CompletableFuture<Void> enviar(PublishRequest requisicao) {
        return CompletableFuture.runAsync(() -> snsClient.publish(requisicao), executor);
    }
}

package br.com.fluxocaixa.consolidado.adapters.in.messaging;

import br.com.fluxocaixa.consolidado.config.PropriedadesAplicacao;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.transport.ReceiverContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Long polling de 20s, lotes de até 10 e processamento em virtual threads.
 * A mensagem só é apagada depois do commit. No desligamento o poller para antes do restante da aplicação.
 */
public class ConsumidorSqsLancamentos implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorSqsLancamentos.class);

    private final SqsClient sqs;
    private final String fila;
    private final int esperaSegundos;
    private final int maximoMensagens;
    private final ProcessadorEventoLancamento processador;
    private final ObservationRegistry observationRegistry;
    private final AtomicBoolean executando = new AtomicBoolean(false);
    private volatile Thread poller;
    private volatile ExecutorService trabalhadores;

    public ConsumidorSqsLancamentos(
            SqsClient sqs,
            PropriedadesAplicacao propriedades,
            ProcessadorEventoLancamento processador,
            ObservationRegistry observationRegistry) {
        this.sqs = sqs;
        this.fila = propriedades.messaging().queueUrl();
        if (fila == null || fila.isBlank()) {
            throw new IllegalStateException("CONSOLIDADO_QUEUE_URL é obrigatória quando o consumo está habilitado");
        }
        this.esperaSegundos = Math.clamp(propriedades.messaging().waitTimeSeconds(), 0, 20);
        this.maximoMensagens = Math.clamp(propriedades.messaging().maxMessages(), 1, 10);
        this.processador = processador;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void start() {
        if (!executando.compareAndSet(false, true)) {
            return;
        }
        trabalhadores = Executors.newVirtualThreadPerTaskExecutor();
        poller = Thread.ofVirtual().name("sqs-consolidador").start(this::loop);
        log.info("Consumidor SQS iniciado. fila={}", fila);
    }

    @Override
    public void stop() {
        if (!executando.compareAndSet(true, false)) {
            return;
        }
        Thread thread = poller;
        if (thread != null) {
            try {
                thread.join(java.time.Duration.ofSeconds(esperaSegundos + 10L));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        ExecutorService executor = trabalhadores;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
        log.info("Consumidor SQS encerrado");
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return executando.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void loop() {
        while (executando.get()) {
            List<Message> mensagens;
            try {
                mensagens = sqs.receiveMessage(pedido -> pedido
                        .queueUrl(fila)
                        .maxNumberOfMessages(maximoMensagens)
                        .waitTimeSeconds(esperaSegundos)
                        .messageAttributeNames("All")).messages();
            } catch (SdkException ex) {
                if (!executando.get()) {
                    break;
                }
                log.warn("Falha ao receber mensagens da fila {}", fila, ex);
                pausar();
                continue;
            }
            if (mensagens == null || mensagens.isEmpty()) {
                continue;
            }
            List<Future<?>> tarefas = new ArrayList<>();
            for (Message mensagem : mensagens) {
                tarefas.add(trabalhadores.submit(() -> processar(mensagem)));
            }
            for (Future<?> tarefa : tarefas) {
                try {
                    tarefa.get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception ex) {
                    log.error("Falha inesperada no processamento concorrente", ex);
                }
            }
        }
    }

    private void processar(Message mensagem) {
        ReceiverContext<Message> contexto = new ReceiverContext<>(this::extrairAtributo);
        contexto.setCarrier(mensagem);
        Observation observation = Observation.start(
                "consolidado.processar.evento",
                () -> contexto,
                observationRegistry);
        try (Observation.Scope ignored = observation.openScope()) {
            DesfechoConsumo desfecho = processador.consumir(new MensagemRecebida(
                    mensagem.body(),
                    atributos(mensagem),
                    mensagem.receiptHandle()));
            switch (desfecho) {
                case PROCESSADO, DUPLICADO -> {
                }
                case POISON, FALHA -> observation.error(
                        new IllegalStateException("Falha ao consumir evento: " + desfecho));
            }
        } catch (RuntimeException ex) {
            observation.error(ex);
            throw ex;
        } finally {
            observation.stop();
        }
    }

    private String extrairAtributo(Message mensagem, String chave) {
        if (mensagem == null || mensagem.messageAttributes() == null) {
            return null;
        }
        MessageAttributeValue direto = mensagem.messageAttributes().get(chave);
        if (direto != null && direto.stringValue() != null) {
            return direto.stringValue();
        }
        for (Map.Entry<String, MessageAttributeValue> entrada : mensagem.messageAttributes().entrySet()) {
            if (entrada.getKey().equalsIgnoreCase(chave) && entrada.getValue() != null) {
                return entrada.getValue().stringValue();
            }
        }
        return null;
    }

    private static Map<String, String> atributos(Message mensagem) {
        Map<String, String> mapa = new HashMap<>();
        if (mensagem.messageAttributes() == null) {
            return mapa;
        }
        mensagem.messageAttributes().forEach((chave, valor) -> {
            if (valor != null && valor.stringValue() != null) {
                mapa.put(chave, valor.stringValue());
            }
        });
        return mapa;
    }

    private void pausar() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

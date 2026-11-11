package br.com.fluxocaixa.consolidado.adapters.in.messaging;

import br.com.fluxocaixa.consolidado.application.comando.ComandoAplicarLancamento;
import br.com.fluxocaixa.consolidado.application.port.in.AplicarLancamentoUseCase;
import br.com.fluxocaixa.consolidado.application.resultado.ResultadoAplicacao;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.retry.Retry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

public class ProcessadorEventoLancamento {

    public static final String LOG_EVENTO = "Evento de lançamento consolidado";

    private static final Logger log = LoggerFactory.getLogger(ProcessadorEventoLancamento.class);

    private final TradutorEventoLancamento tradutor;
    private final AplicarLancamentoUseCase aplicarLancamento;
    private final MetricasConsumo metricas;
    private final ConfirmacaoFila confirmacao;
    private final Retry retryBanco;
    private final Bulkhead bulkhead;
    private final Tracer tracer;

    public ProcessadorEventoLancamento(
            TradutorEventoLancamento tradutor,
            AplicarLancamentoUseCase aplicarLancamento,
            MetricasConsumo metricas,
            ConfirmacaoFila confirmacao,
            Retry retryBanco,
            Bulkhead bulkhead,
            Tracer tracer) {
        this.tradutor = tradutor;
        this.aplicarLancamento = aplicarLancamento;
        this.metricas = metricas;
        this.confirmacao = confirmacao;
        this.retryBanco = retryBanco;
        this.bulkhead = bulkhead;
        this.tracer = tracer == null ? Tracer.NOOP : tracer;
    }

    public DesfechoConsumo consumir(MensagemRecebida mensagem) {
        try {
            return bulkhead.executeSupplier(() -> consumirDentro(mensagem));
        } catch (BulkheadFullException ex) {
            log.warn("Bulkhead do consumo SQS esgotado; a mensagem permanece na fila");
            metricas.falha();
            return DesfechoConsumo.FALHA;
        }
    }

    private DesfechoConsumo consumirDentro(MensagemRecebida mensagem) {
        try {
            ComandoAplicarLancamento comando = tradutor.traduzir(
                    mensagem.corpo(),
                    mensagem.atributos() == null ? Map.of() : mensagem.atributos());
            marcarEvento(comando);
            // O UPSERT e a tabela eventos_processados tornam a nova tentativa idempotente.
            ResultadoAplicacao resultado = retryBanco.executeSupplier(() -> aplicarLancamento.executar(comando));
            log.info(LOG_EVENTO);
            return switch (resultado) {
                case ResultadoAplicacao.Processado processado -> {
                    metricas.processado(processado.atraso());
                    apagar(mensagem.receiptHandle());
                    yield DesfechoConsumo.PROCESSADO;
                }
                case ResultadoAplicacao.Duplicado duplicado -> {
                    metricas.duplicado(duplicado.atraso());
                    apagar(mensagem.receiptHandle());
                    yield DesfechoConsumo.DUPLICADO;
                }
            };
        } catch (MensagemInvalidaException ex) {
            log.error(
                    "Mensagem inválida (poison); tamanhoCorpo={}. Não será removida e seguirá para a DLQ após o maxReceiveCount",
                    mensagem.corpo() == null ? 0 : mensagem.corpo().length());
            metricas.falha();
            return DesfechoConsumo.POISON;
        } catch (RuntimeException ex) {
            log.error("Falha ao processar mensagem após as tentativas; ela será reentregue pela fila", ex);
            metricas.falha();
            return DesfechoConsumo.FALHA;
        } finally {
            MDC.remove("eventId");
            MDC.remove("comercianteId");
        }
    }

    private void marcarEvento(ComandoAplicarLancamento comando) {
        String eventId = comando.eventId().toString();
        String comercianteId = comando.comercianteId().toString();
        MDC.put("eventId", eventId);
        MDC.put("comercianteId", comercianteId);
        Span span = tracer.currentSpan();
        if (span != null) {
            span.tag("event.id", eventId);
            span.tag("comerciante.id", comercianteId);
        }
    }

    private void apagar(String receiptHandle) {
        try {
            confirmacao.apagar(receiptHandle);
        } catch (RuntimeException ex) {
            log.error("Evento tratado, mas a exclusão da mensagem falhou; a reentrega será ignorada pela idempotência", ex);
            metricas.falha();
        }
    }

    @FunctionalInterface
    public interface ConfirmacaoFila {
        void apagar(String receiptHandle);
    }
}

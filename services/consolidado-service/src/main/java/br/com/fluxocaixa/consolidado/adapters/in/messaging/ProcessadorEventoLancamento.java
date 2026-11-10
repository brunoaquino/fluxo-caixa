package br.com.fluxocaixa.consolidado.adapters.in.messaging;

import br.com.fluxocaixa.consolidado.application.comando.ComandoAplicarLancamento;
import br.com.fluxocaixa.consolidado.application.port.in.AplicarLancamentoUseCase;
import br.com.fluxocaixa.consolidado.application.resultado.ResultadoAplicacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ProcessadorEventoLancamento {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorEventoLancamento.class);

    private final TradutorEventoLancamento tradutor;
    private final AplicarLancamentoUseCase aplicarLancamento;
    private final MetricasConsumo metricas;
    private final ConfirmacaoFila confirmacao;

    public ProcessadorEventoLancamento(
            TradutorEventoLancamento tradutor,
            AplicarLancamentoUseCase aplicarLancamento,
            MetricasConsumo metricas,
            ConfirmacaoFila confirmacao) {
        this.tradutor = tradutor;
        this.aplicarLancamento = aplicarLancamento;
        this.metricas = metricas;
        this.confirmacao = confirmacao;
    }

    public DesfechoConsumo consumir(MensagemRecebida mensagem) {
        try {
            ComandoAplicarLancamento comando = tradutor.traduzir(
                    mensagem.corpo(),
                    mensagem.atributos() == null ? Map.of() : mensagem.atributos());
            ResultadoAplicacao resultado = aplicarLancamento.executar(comando);
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
                    "Mensagem inválida (poison); não será removida e seguirá para a DLQ após o maxReceiveCount. corpo={}",
                    resumir(mensagem.corpo()),
                    ex);
            metricas.falha();
            return DesfechoConsumo.POISON;
        } catch (RuntimeException ex) {
            log.error("Falha transitória ao processar mensagem; ela será reentregue pela fila", ex);
            metricas.falha();
            return DesfechoConsumo.FALHA;
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

    private static String resumir(String corpo) {
        if (corpo == null) {
            return "";
        }
        return corpo.length() <= 2000 ? corpo : corpo.substring(0, 2000) + "...";
    }

    @FunctionalInterface
    public interface ConfirmacaoFila {
        void apagar(String receiptHandle);
    }
}

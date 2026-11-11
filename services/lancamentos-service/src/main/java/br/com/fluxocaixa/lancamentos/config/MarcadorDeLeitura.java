package br.com.fluxocaixa.lancamentos.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Marca a thread antes de o transaction manager pedir conexão, para o
 * roteador escolher a réplica em {@code @Transactional(readOnly = true)}.
 */
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class MarcadorDeLeitura implements TransactionExecutionListener {

    private final ThreadLocal<Boolean> somenteLeitura = new ThreadLocal<>();

    @Override
    public void beforeBegin(TransactionExecution execution) {
        somenteLeitura.set(execution.isReadOnly());
    }

    @Override
    public void afterBegin(TransactionExecution execution, Throwable beginFailure) {
        if (beginFailure != null) {
            somenteLeitura.remove();
        }
    }

    @Override
    public void afterCommit(TransactionExecution execution, Throwable commitFailure) {
        somenteLeitura.remove();
    }

    @Override
    public void afterRollback(TransactionExecution execution, Throwable rollbackFailure) {
        somenteLeitura.remove();
    }

    public Boolean somenteLeitura() {
        return somenteLeitura.get();
    }

    public void marcar(boolean leitura) {
        somenteLeitura.set(leitura);
    }

    public void limpar() {
        somenteLeitura.remove();
    }
}

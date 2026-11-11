package br.com.fluxocaixa.lancamentos.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Role;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;

import java.util.ArrayList;

@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
class RegistradorDeMarcador implements BeanPostProcessor {

    private final MarcadorDeLeitura marcador;

    RegistradorDeMarcador(MarcadorDeLeitura marcador) {
        this.marcador = marcador;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof AbstractPlatformTransactionManager transacao) {
            var ouvintes = new ArrayList<>(transacao.getTransactionExecutionListeners());
            if (!ouvintes.contains(marcador)) {
                ouvintes.add(marcador);
                transacao.setTransactionExecutionListeners(ouvintes);
            }
        }
        return bean;
    }
}

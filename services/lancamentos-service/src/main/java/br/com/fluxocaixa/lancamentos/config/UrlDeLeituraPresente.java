package br.com.fluxocaixa.lancamentos.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class UrlDeLeituraPresente implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty("DB_READER_URL");
        return url != null && !url.isBlank();
    }
}

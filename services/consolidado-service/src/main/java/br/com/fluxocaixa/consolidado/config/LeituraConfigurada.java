package br.com.fluxocaixa.consolidado.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class LeituraConfigurada implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String url = context.getEnvironment().getProperty("app.datasource.reader-url");
        return url != null && !url.isBlank();
    }
}

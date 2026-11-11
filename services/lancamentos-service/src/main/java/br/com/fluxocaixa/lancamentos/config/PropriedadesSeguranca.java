package br.com.fluxocaixa.lancamentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.security")
public record PropriedadesSeguranca(
        String comercianteClaim,
        String scopePrefix,
        @DefaultValue("false") Boolean swaggerPublico
) {

    public PropriedadesSeguranca {
        if (comercianteClaim == null || comercianteClaim.isBlank()) {
            comercianteClaim = "comerciante_id";
        }
        if (scopePrefix == null) {
            scopePrefix = "";
        }
        swaggerPublico = Boolean.TRUE.equals(swaggerPublico);
    }

    public boolean swaggerAberto() {
        return Boolean.TRUE.equals(swaggerPublico);
    }

    public String autoridade(String escopo) {
        return "SCOPE_" + scopePrefix + escopo;
    }
}

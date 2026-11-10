package br.com.fluxocaixa.lancamentos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record PropriedadesSeguranca(String comercianteClaim, String scopePrefix) {

    public PropriedadesSeguranca {
        if (comercianteClaim == null || comercianteClaim.isBlank()) {
            comercianteClaim = "comerciante_id";
        }
        if (scopePrefix == null) {
            scopePrefix = "";
        }
    }

    public String autoridade(String escopo) {
        return "SCOPE_" + scopePrefix + escopo;
    }
}

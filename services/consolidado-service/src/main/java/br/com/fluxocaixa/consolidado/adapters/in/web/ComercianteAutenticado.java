package br.com.fluxocaixa.consolidado.adapters.in.web;

import br.com.fluxocaixa.consolidado.config.PropriedadesAplicacao;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ComercianteAutenticado {

    private final String claim;

    public ComercianteAutenticado(PropriedadesAplicacao propriedades) {
        this.claim = propriedades.security().comercianteClaim();
    }

    public UUID atual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacao instanceof JwtAuthenticationToken token)) {
            throw new ComercianteNaoIdentificadoException("Comerciante não identificado no token");
        }
        Object valor = token.getToken().getClaim(claim);
        if (valor == null) {
            throw new ComercianteNaoIdentificadoException("Claim " + claim + " ausente no token");
        }
        try {
            return UUID.fromString(valor.toString());
        } catch (IllegalArgumentException ex) {
            throw new ComercianteNaoIdentificadoException("Claim " + claim + " não é um UUID válido");
        }
    }
}

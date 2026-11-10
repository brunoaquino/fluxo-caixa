package br.com.fluxocaixa.lancamentos.adapters.in.web;

import br.com.fluxocaixa.lancamentos.adapters.in.web.excecao.ComercianteNaoIdentificadoException;
import br.com.fluxocaixa.lancamentos.config.PropriedadesSeguranca;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ComercianteAutenticado {

    private final PropriedadesSeguranca propriedades;

    public ComercianteAutenticado(PropriedadesSeguranca propriedades) {
        this.propriedades = propriedades;
    }

    public UUID exigir() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof Jwt jwt)) {
            throw new ComercianteNaoIdentificadoException("Token JWT ausente");
        }
        String claim = propriedades.comercianteClaim();
        Object valor = jwt.getClaim(claim);
        if (valor == null || valor.toString().isBlank()) {
            throw new ComercianteNaoIdentificadoException("Claim " + claim + " ausente no token");
        }
        try {
            return UUID.fromString(valor.toString());
        } catch (IllegalArgumentException ex) {
            throw new ComercianteNaoIdentificadoException("Claim " + claim + " não é um UUID");
        }
    }
}

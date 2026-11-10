package br.com.fluxocaixa.lancamentos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Quando {@code JWT_JWK_SET_URI} está definido (Keycloak na rede do compose),
 * as chaves vêm desse endpoint e o issuer do token continua sendo validado
 * contra {@code JWT_ISSUER_URI}.
 */
@Configuration
public class DecodificadorJwtConfig {

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${JWT_ISSUER_URI}") String issuer,
            @Value("${JWT_JWK_SET_URI:}") String jwkSetUri
    ) {
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            return JwtDecoders.fromIssuerLocation(issuer);
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}

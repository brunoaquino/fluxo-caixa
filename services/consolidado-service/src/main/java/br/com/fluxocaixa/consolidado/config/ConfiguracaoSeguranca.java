package br.com.fluxocaixa.consolidado.config;

import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties(PropriedadesAplicacao.class)
public class ConfiguracaoSeguranca {

    static final String ESCOPO_LEITURA = "consolidado:read";

    @Bean
    SecurityFilterChain filtroSeguranca(HttpSecurity http, PropriedadesAplicacao propriedades, JsonMapper jsonMapper)
            throws Exception {
        String autoridade = autoridadeLeitura(propriedades.security().scopePrefix());
        if (propriedades.security().corsAllowedOrigins().isEmpty()) {
            http.cors(AbstractHttpConfigurer::disable);
        } else {
            List<String> origens = propriedades.security().corsAllowedOrigins();
            http.cors(cors -> cors.configurationSource(pedido -> corsRestrito(origens)));
        }
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                        .permissionsPolicyHeader(permissions -> permissions.policy(
                                "geolocation=(), microphone=(), camera=()"))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/actuator/info",
                                    "/actuator/prometheus")
                            .permitAll();
                    if (propriedades.security().swaggerPublico()) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll();
                    }
                    auth.requestMatchers("/api/v1/consolidados/**").hasAuthority(autoridade);
                    auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> escrever(
                                jsonMapper,
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "Não autenticado",
                                "Credencial ausente ou inválida",
                                URI.create("https://fluxocaixa.com.br/problems/nao-autenticado")))
                        .accessDeniedHandler((request, response, accessDeniedException) -> escrever(
                                jsonMapper,
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "Acesso negado",
                                "Escopo insuficiente para consultar o consolidado",
                                URI.create("https://fluxocaixa.com.br/problems/acesso-negado"))))
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Profile("!test")
    JwtDecoder decodificadorJwt(PropriedadesAplicacao propriedades) {
        String emissor = propriedades.security().jwt().issuerUri();
        if (emissor == null || emissor.isBlank()) {
            throw new IllegalStateException("JWT_ISSUER_URI é obrigatório");
        }
        String jwk = propriedades.security().jwt().jwkSetUri();
        if (jwk != null && !jwk.isBlank()) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwk).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(emissor));
            return decoder;
        }
        return JwtDecoders.fromIssuerLocation(emissor);
    }

    private static CorsConfiguration corsRestrito(List<String> origens) {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOrigins(origens);
        configuracao.setAllowedMethods(List.of(HttpMethod.GET.name(), HttpMethod.OPTIONS.name()));
        configuracao.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Correlation-Id", "traceparent", "tracestate"));
        configuracao.setExposedHeaders(List.of("X-Correlation-Id"));
        configuracao.setAllowCredentials(false);
        configuracao.setMaxAge(Duration.ofMinutes(10));
        return configuracao;
    }

    static String autoridadeLeitura(String prefixo) {
        String prefixoNormalizado = prefixo == null ? "" : prefixo;
        return "SCOPE_" + prefixoNormalizado + ESCOPO_LEITURA;
    }

    private static void escrever(
            JsonMapper jsonMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String titulo,
            String detalhe,
            URI tipo) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        problema.setType(tipo);
        problema.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), problema);
    }
}

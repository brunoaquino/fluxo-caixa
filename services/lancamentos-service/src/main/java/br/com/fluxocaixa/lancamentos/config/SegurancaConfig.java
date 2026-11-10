package br.com.fluxocaixa.lancamentos.config;

import br.com.fluxocaixa.lancamentos.adapters.in.web.FiltroCorrelacao;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

@Configuration
@EnableWebSecurity
public class SegurancaConfig {

    @Bean
    SecurityFilterChain cadeiaDeSeguranca(
            HttpSecurity http,
            PropriedadesSeguranca propriedades,
            EscritorDeProblema escritor
    ) throws Exception {
        String escrever = propriedades.autoridade("lancamentos:write");
        String ler = propriedades.autoridade("lancamentos:read");
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'"))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/lancamentos").hasAuthority(escrever)
                        .requestMatchers(HttpMethod.POST, "/api/v1/lancamentos/*/estornos").hasAuthority(escrever)
                        .requestMatchers(HttpMethod.GET, "/api/v1/lancamentos", "/api/v1/lancamentos/*").hasAuthority(ler)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> escritor.escrever(
                                response,
                                HttpStatus.UNAUTHORIZED,
                                "nao-autenticado",
                                "Não autenticado",
                                "Autenticação necessária"))
                        .accessDeniedHandler((request, response, accessDeniedException) -> escritor.escrever(
                                response,
                                HttpStatus.FORBIDDEN,
                                "acesso-negado",
                                "Acesso negado",
                                "Escopo insuficiente para esta operação")));
        return http.build();
    }

    @Bean
    FilterRegistrationBean<FiltroCorrelacao> filtroCorrelacao() {
        FilterRegistrationBean<FiltroCorrelacao> registro = new FilterRegistrationBean<>(new FiltroCorrelacao());
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }
}

package br.com.fluxocaixa.consolidado.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfiguracaoOpenApi {

    public static final String ESQUEMA = "bearer-jwt";

    @Bean
    OpenAPI consolidadoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Consolidado API")
                        .version("v1")
                        .description("Consulta do saldo diário consolidado e do relatório por período"))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA))
                .components(new Components().addSecuritySchemes(ESQUEMA, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}

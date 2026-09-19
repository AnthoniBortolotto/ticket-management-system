package com.ticketsystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do contrato publicado em {@code /v3/api-docs}.
 *
 * <p>Este schema e a fonte dos tipos TypeScript do frontend ({@code pnpm gen:api}), entao
 * ele nao e enfeite de Swagger: se ele mentir, a tela quebra com o compilador verde.
 */
@Configuration
class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    OpenAPI ticketSystemOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Ticket Management System API")
                .version("v1")
                .description("""
                    Helpdesk com atribuicao por equipe, visibilidade por papel e SLA \
                    calculado em horario de trabalho.""")
                .license(new License().name("MIT")))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}

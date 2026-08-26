package dev.codexofrealms;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String BEARER_SCHEME = "bearer-jwt";

    @Bean
    OpenAPI codexOfRealmsOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Codex of Realms API")
                .version("v1")
                .description("Evidence-first, access-aware lore management API."))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}

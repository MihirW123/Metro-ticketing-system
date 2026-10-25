package com.metro.ticketing.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Basic Authentication";

    @Bean
    public OpenAPI metroTicketingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Metro Ticketing System API")
                        .description("REST API documentation for Metro Ticketing System, covering Stations, Trains, Routes, Schedules, Fares, Tickets, Metro Cards, Payments, and Maintenance.")
                        .version("1.0.0")
                        .contact(new Contact().name("Metro Transit Authority Support"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Enter your database username (contact) and password.")));
    }
}

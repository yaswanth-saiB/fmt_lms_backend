package com.fmt.fmt_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public OpenAPI customOpenAPI() {

        // Define the security scheme (JWT Bearer)
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Enter JWT token: Bearer &lt;token&gt;");

        // Add security requirement to all endpoints by default
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("BearerAuth");

        return new OpenAPI()
                .info(new Info()
                        .title("Trading App API")
                        .description("Complete Authentication and User Management API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Trading App Team")
                                .email("help@firstmilliontrade.com")
                                .url("https://tradingapp.com"))
                        .license(new License()
                                .name("Private API")
                                .url("https://tradingapp.com/terms")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development"),
                        new Server()
                                .url(baseUrl)
                                .description("Current Server")
                ))
                // Add security scheme
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", securityScheme))
                // Apply security globally
                .addSecurityItem(securityRequirement);
    }
}
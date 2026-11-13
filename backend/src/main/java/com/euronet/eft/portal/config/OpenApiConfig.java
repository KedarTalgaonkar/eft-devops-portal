package com.euronet.eft.portal.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI portalOpenApi() {
        return new OpenAPI().info(new Info()
                .title("EFT DevOps Portal API")
                .version("v1")
                .description("Inventory, Jenkins pipelines, GitHub, SonarQube, SBOM, Artifactory and monitoring for EFT applications."));
    }
}

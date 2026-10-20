package com.commerceflow.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Document-wide metadata only; endpoint documentation stays next to each module's controller. */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI commerceFlowOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CommerceFlow API")
                .description("E-commerce backend. Errors use RFC 9457 problem details (application/problem+json).")
                .version("v1"));
    }
}

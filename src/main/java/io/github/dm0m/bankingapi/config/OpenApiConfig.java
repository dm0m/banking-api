package io.github.dm0m.bankingapi.config;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ProblemDetail;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Banking API",
        version = "v1",
        description = "Customers, accounts, deposits and transfers between accounts."))
public class OpenApiConfig {

    private static final String PROBLEM_DETAIL = "ProblemDetail";

    // every 4xx/5xx is a ProblemDetail, so controllers only declare the status code and what it means
    @Bean
    public OpenApiCustomizer problemDetailErrorResponses() {
        return openApi -> {
            ModelConverters.getInstance().read(ProblemDetail.class).forEach(openApi.getComponents()::addSchemas);
            // Jackson writes ProblemDetail's extra properties (e.g. "errors") flat, not under a "properties" key
            Schema<?> problemDetail = openApi.getComponents().getSchemas().get(PROBLEM_DETAIL);
            problemDetail.getProperties().remove("properties");
            problemDetail.setAdditionalProperties(true);

            // overrides unconditionally: springdoc fills a body-less @ApiResponse with the method's return type,
            // so an error response can't be told apart from one that declared its own body
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation ->
                    operation.getResponses().forEach((code, response) -> {
                        if (code.startsWith("4") || code.startsWith("5")) {
                            response.setContent(new Content().addMediaType("application/problem+json",
                                    new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL))));
                        }
                    })));
        };
    }
}

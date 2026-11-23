package com.spirecentral.dfcbridge.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Adds the shared error responses to each operation marked with {@link ErrorResponses}.
 * An entry the operation already has for a status is kept.
 */
@Component
public class ErrorResponsesCustomizer implements OperationCustomizer {

    private static final String JSON = "application/json";
    private static final String ERROR_SCHEMA_REF = "#/components/schemas/ErrorResponse";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        ErrorResponses marker = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ErrorResponses.class);
        if (marker == null) {
            marker = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ErrorResponses.class);
        }
        if (marker == null) {
            return operation;
        }

        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        ApiResponses responses = operation.getResponses();
        add(responses, "406", "The Accept header does not allow application/json");
        if (operation.getRequestBody() != null) {
            add(responses, "415", "The request body is not application/json");
        }
        add(responses, "500", "Bridge error with no DFC cause");
        if (marker.session()) {
            add(responses, "400", "Invalid request: a missing or invalid parameter or body, "
                    + "a value the bridge rejects, or a request DFC rejects");
            add(responses, "401", "DFC authentication failed");
            add(responses, "403", "No permission in the repository");
            add(responses, "404", "Session not found or expired, or a name or id that DFC was asked for does not exist");
            add(responses, "502", "Other DFC error");
            add(responses, "503", "Docbroker or repository unreachable");
        }
        return operation;
    }

    private static void add(ApiResponses responses, String status, String description) {
        responses.putIfAbsent(status, new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(JSON,
                        new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF)))));
    }
}

package com.spirecentral.dfcbridge.config;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ErrorResponsesCustomizerTest {

    private final ErrorResponsesCustomizer customizer = new ErrorResponsesCustomizer();

    @Test
    void annotatedClass_getsTheCommonSet() throws Exception {
        Operation operation = customize(new CommonController(), new Operation());

        assertEquals(Set.of("406", "500"), operation.getResponses().keySet());
    }

    @Test
    void annotatedClassWithSession_getsTheCommonAndSessionSets() throws Exception {
        Operation operation = customize(new SessionController(), new Operation());

        assertEquals(Set.of("400", "401", "403", "404", "406", "500", "502", "503"),
                operation.getResponses().keySet());
    }

    @Test
    void annotatedMethod_getsTheCommonSet() throws Exception {
        Operation operation = customize(new MethodAnnotatedController(), new Operation());

        assertEquals(Set.of("406", "500"), operation.getResponses().keySet());
    }

    @Test
    void entryTheOperationAlreadyHas_isKept() throws Exception {
        ApiResponse own = new ApiResponse().description("Object or session not found");
        Operation operation = new Operation().responses(new ApiResponses().addApiResponse("404", own));

        customize(new SessionController(), operation);

        assertSame(own, operation.getResponses().get("404"));
    }

    @Test
    void operationWithRequestBody_gets415() throws Exception {
        Operation operation = customize(new CommonController(), new Operation().requestBody(new RequestBody()));

        assertEquals(Set.of("406", "415", "500"), operation.getResponses().keySet());
    }

    @Test
    void operationWithoutAnnotation_isUnchanged() throws Exception {
        Operation operation = new Operation();

        customize(new PlainController(), operation);

        assertEquals(null, operation.getResponses());
    }

    private Operation customize(Object controller, Operation operation) throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(controller, controller.getClass().getMethod("handle"));
        return customizer.customize(operation, handlerMethod);
    }

    @ErrorResponses
    public static class CommonController {
        public void handle() {
        }
    }

    @ErrorResponses(session = true)
    public static class SessionController {
        public void handle() {
        }
    }

    public static class MethodAnnotatedController {
        @ErrorResponses
        public void handle() {
        }
    }

    public static class PlainController {
        public void handle() {
        }
    }
}

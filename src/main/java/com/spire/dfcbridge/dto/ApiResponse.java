package com.spire.dfcbridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response from an API method execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from a DFC API method execution")
public class ApiResponse {

    @Schema(description = "The result of the method invocation")
    private Object result;

    @Schema(description = "The result type")
    private String resultType;

    @Schema(description = "Execution time in milliseconds")
    private long executionTimeMs;
}

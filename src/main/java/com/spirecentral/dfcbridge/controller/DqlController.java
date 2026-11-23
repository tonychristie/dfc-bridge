package com.spirecentral.dfcbridge.controller;

import com.spirecentral.dfcbridge.config.ErrorResponses;
import com.spirecentral.dfcbridge.dto.DqlRequest;
import com.spirecentral.dfcbridge.model.QueryResult;
import com.spirecentral.dfcbridge.service.DqlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for DQL query execution.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "DQL", description = "DQL query execution")
@ErrorResponses(session = true)
public class DqlController {

    private final DqlService dqlService;

    public DqlController(DqlService dqlService) {
        this.dqlService = dqlService;
    }

    @PostMapping("/dql")
    @Operation(
        summary = "Execute DQL query",
        description = "Executes a DQL SELECT query and returns the results. " +
                      "Supports pagination via maxRows and startRow parameters."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Query executed successfully",
            content = @Content(schema = @Schema(implementation = QueryResult.class))
        )
    })
    public ResponseEntity<QueryResult> executeQuery(@Valid @RequestBody DqlRequest request) {
        QueryResult result = dqlService.executeQuery(request);
        return ResponseEntity.ok(result);
    }
}

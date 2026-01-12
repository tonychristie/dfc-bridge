package com.spirecentral.dfcbridge.controller;

import com.spirecentral.dfcbridge.dto.ApiRequest;
import com.spirecentral.dfcbridge.dto.ApiResponse;
import com.spirecentral.dfcbridge.dto.CreateObjectRequest;
import com.spirecentral.dfcbridge.dto.DmApiRequest;
import com.spirecentral.dfcbridge.dto.ErrorResponse;
import com.spirecentral.dfcbridge.dto.UpdateObjectRequest;
import com.spirecentral.dfcbridge.model.ObjectInfo;
import com.spirecentral.dfcbridge.model.TypeInfo;
import com.spirecentral.dfcbridge.service.DmApiService;
import com.spirecentral.dfcbridge.service.ObjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Documentum object operations.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Objects", description = "Documentum object operations")
public class ObjectController {

    private static final Logger log = LoggerFactory.getLogger(ObjectController.class);

    private final ObjectService objectService;
    private final DmApiService dmApiService;

    public ObjectController(ObjectService objectService, DmApiService dmApiService) {
        this.objectService = objectService;
        this.dmApiService = dmApiService;
    }

    @GetMapping("/objects/{objectId}")
    @Operation(
        summary = "Get object by ID",
        description = "Retrieves a Documentum object by its r_object_id"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Object retrieved successfully",
            content = @Content(schema = @Schema(implementation = ObjectInfo.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Object not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<ObjectInfo> getObject(
            @Parameter(description = "Object ID (r_object_id)") @PathVariable String objectId,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        ObjectInfo info = objectService.getObject(sessionId, objectId);
        return ResponseEntity.ok(info);
    }

    @GetMapping("/cabinets")
    @Operation(
        summary = "List cabinets",
        description = "Lists all cabinets in the repository"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Cabinets retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectInfo.class)))
        )
    })
    public ResponseEntity<List<ObjectInfo>> getCabinets(
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        List<ObjectInfo> cabinets = objectService.getCabinets(sessionId);
        return ResponseEntity.ok(cabinets);
    }

    @GetMapping("/objects/{folderId}/contents")
    @Operation(
        summary = "List folder contents by ID",
        description = "Lists all objects in a folder by its object ID"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Folder contents retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectInfo.class)))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Folder not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<List<ObjectInfo>> listFolderContentsById(
            @Parameter(description = "Folder object ID") @PathVariable String folderId,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        List<ObjectInfo> contents = objectService.listFolderContentsById(sessionId, folderId);
        return ResponseEntity.ok(contents);
    }

    @PostMapping("/objects/{objectId}")
    @Operation(
        summary = "Update object",
        description = "Updates attributes of a Documentum object"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Object updated successfully",
            content = @Content(schema = @Schema(implementation = ObjectInfo.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Object not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<ObjectInfo> updateObject(
            @Parameter(description = "Object ID (r_object_id)") @PathVariable String objectId,
            @Valid @RequestBody UpdateObjectRequest request) {
        ObjectInfo info = objectService.updateObject(request.getSessionId(), objectId, request);
        return ResponseEntity.ok(info);
    }

    @GetMapping("/folders/{*folderPath}")
    @Operation(
        summary = "List folder contents",
        description = "Lists all objects in a folder by path (e.g., /Temp, /Cabinet/Folder)"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Folder contents retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ObjectInfo.class)))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Folder not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<List<ObjectInfo>> listFolderContents(
            @Parameter(description = "Folder path") @PathVariable String folderPath,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        // Ensure path starts with /
        if (!folderPath.startsWith("/")) {
            folderPath = "/" + folderPath;
        }
        List<ObjectInfo> contents = objectService.listFolderContents(sessionId, folderPath);
        return ResponseEntity.ok(contents);
    }

    @GetMapping("/types")
    @Operation(
        summary = "List object types",
        description = "Lists Documentum object types, optionally filtered by pattern"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Types retrieved",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = TypeInfo.class)))
        )
    })
    public ResponseEntity<List<TypeInfo>> listTypes(
            @Parameter(description = "Session ID") @RequestParam String sessionId,
            @Parameter(description = "Type name pattern (e.g., dm_*)") @RequestParam(required = false) String pattern) {
        List<TypeInfo> types = objectService.listTypes(sessionId, pattern);
        return ResponseEntity.ok(types);
    }

    @GetMapping("/types/{typeName}")
    @Operation(
        summary = "Get type info",
        description = "Gets detailed information about a Documentum object type"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Type info retrieved",
            content = @Content(schema = @Schema(implementation = TypeInfo.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Type not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<TypeInfo> getTypeInfo(
            @Parameter(description = "Type name") @PathVariable String typeName,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        TypeInfo info = objectService.getTypeInfo(sessionId, typeName);
        return ResponseEntity.ok(info);
    }

    @PostMapping("/api")
    @Operation(
        summary = "Execute DFC API method",
        description = "Executes an arbitrary DFC API method on an object or type"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "API method executed",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Object or type not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "API execution error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<ApiResponse> executeApi(@Valid @RequestBody ApiRequest request) {
        ApiResponse response = objectService.executeApi(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/dmapi")
    @Operation(
        summary = "Execute dmAPI command",
        description = "Executes a dmAPI command via session.apiGet(), apiExec(), or apiSet(). " +
                "These are server-level API calls distinct from DFC object method invocations."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "dmAPI command executed",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "dmAPI execution error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<ApiResponse> executeDmApi(@Valid @RequestBody DmApiRequest request) {
        log.debug("executeDmApi: Received request - apiType={}, command={}, sessionId={}",
                request.getApiType(), request.getCommand(), request.getSessionId());

        ApiResponse response = dmApiService.execute(request);

        log.debug("executeDmApi: Returning response - resultType={}, result={}, executionTimeMs={}",
                response.getResultType(), response.getResult(), response.getExecutionTimeMs());

        return ResponseEntity.ok(response);
    }

    // ==================== Version Control Operations ====================

    @PutMapping("/objects/{objectId}/lock")
    @Operation(
        summary = "Checkout object",
        description = "Checks out (locks) a Documentum object for editing"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Object checked out successfully",
            content = @Content(schema = @Schema(implementation = ObjectInfo.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Object not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<ObjectInfo> checkout(
            @Parameter(description = "Object ID (r_object_id)") @PathVariable String objectId,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        ObjectInfo info = objectService.checkout(sessionId, objectId);
        return ResponseEntity.ok(info);
    }

    @DeleteMapping("/objects/{objectId}/lock")
    @Operation(
        summary = "Cancel checkout",
        description = "Cancels the checkout (unlocks) a Documentum object"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "Checkout cancelled successfully"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Object not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<Void> cancelCheckout(
            @Parameter(description = "Object ID (r_object_id)") @PathVariable String objectId,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        objectService.cancelCheckout(sessionId, objectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/objects/{objectId}/versions")
    @Operation(
        summary = "Checkin object",
        description = "Checks in a Documentum object, creating a new version"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Object checked in successfully",
            content = @Content(schema = @Schema(implementation = ObjectInfo.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Object not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<ObjectInfo> checkin(
            @Parameter(description = "Object ID (r_object_id)") @PathVariable String objectId,
            @Parameter(description = "Session ID") @RequestParam String sessionId,
            @Parameter(description = "Version label (e.g., CURRENT, 1.1)") @RequestParam(required = false, defaultValue = "CURRENT") String versionLabel) {
        ObjectInfo info = objectService.checkin(sessionId, objectId, versionLabel);
        return ResponseEntity.ok(info);
    }

    // ==================== Object Lifecycle Operations ====================

    @PostMapping("/objects")
    @Operation(
        summary = "Create object",
        description = "Creates a new Documentum object"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Object created successfully",
            content = @Content(schema = @Schema(implementation = ObjectInfo.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<ObjectInfo> createObject(@Valid @RequestBody CreateObjectRequest request) {
        ObjectInfo info = objectService.createObject(request.getSessionId(), request);
        return ResponseEntity.status(201).body(info);
    }

    @DeleteMapping("/objects/{objectId}")
    @Operation(
        summary = "Delete object",
        description = "Deletes a Documentum object"
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "204",
            description = "Object deleted successfully"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "Object not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<Void> deleteObject(
            @Parameter(description = "Object ID (r_object_id)") @PathVariable String objectId,
            @Parameter(description = "Session ID") @RequestParam String sessionId,
            @Parameter(description = "Delete all versions") @RequestParam(required = false, defaultValue = "false") boolean allVersions) {
        objectService.deleteObject(sessionId, objectId, allVersions);
        return ResponseEntity.noContent().build();
    }
}

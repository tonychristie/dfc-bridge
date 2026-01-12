package com.spirecentral.dfcbridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spirecentral.dfcbridge.dto.ApiRequest;
import com.spirecentral.dfcbridge.dto.ApiResponse;
import com.spirecentral.dfcbridge.dto.DmApiRequest;
import com.spirecentral.dfcbridge.dto.UpdateObjectRequest;
import com.spirecentral.dfcbridge.exception.ObjectNotFoundException;
import com.spirecentral.dfcbridge.exception.SessionNotFoundException;
import com.spirecentral.dfcbridge.model.ObjectInfo;
import com.spirecentral.dfcbridge.model.TypeInfo;
import com.spirecentral.dfcbridge.service.DmApiService;
import com.spirecentral.dfcbridge.service.ObjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ObjectController.class)
class ObjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ObjectService objectService;

    @MockBean
    private DmApiService dmApiService;

    // Tests for getObject endpoint

    @Test
    void testGetObject_Success() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("object_name", "test_document.pdf");
        attributes.put("r_content_size", 1024);

        ObjectInfo info = ObjectInfo.builder()
                .objectId("0900000180000001")
                .type("dm_document")
                .name("test_document.pdf")
                .attributes(attributes)
                .permissionLevel(6)
                .permissionLabel("WRITE")
                .build();

        when(objectService.getObject("session-123", "0900000180000001")).thenReturn(info);

        mockMvc.perform(get("/api/v1/objects/0900000180000001")
                        .param("sessionId", "session-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectId").value("0900000180000001"))
                .andExpect(jsonPath("$.type").value("dm_document"))
                .andExpect(jsonPath("$.name").value("test_document.pdf"))
                .andExpect(jsonPath("$.permissionLabel").value("WRITE"));

        verify(objectService).getObject("session-123", "0900000180000001");
    }

    @Test
    void testGetObject_NotFound() throws Exception {
        when(objectService.getObject("session-123", "nonexistent"))
                .thenThrow(new ObjectNotFoundException("nonexistent"));

        mockMvc.perform(get("/api/v1/objects/nonexistent")
                        .param("sessionId", "session-123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OBJECT_NOT_FOUND"));
    }

    @Test
    void testGetObject_SessionNotFound() throws Exception {
        when(objectService.getObject("invalid-session", "0900000180000001"))
                .thenThrow(new SessionNotFoundException("invalid-session"));

        mockMvc.perform(get("/api/v1/objects/0900000180000001")
                        .param("sessionId", "invalid-session"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    // Tests for updateObject endpoint

    @Test
    void testUpdateObject_Success() throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("object_name", "renamed_document.pdf");

        UpdateObjectRequest request = UpdateObjectRequest.builder()
                .sessionId("session-123")
                .attributes(attributes)
                .save(true)
                .build();

        ObjectInfo updatedInfo = ObjectInfo.builder()
                .objectId("0900000180000001")
                .type("dm_document")
                .name("renamed_document.pdf")
                .build();

        when(objectService.updateObject(eq("session-123"), eq("0900000180000001"), any(UpdateObjectRequest.class)))
                .thenReturn(updatedInfo);

        mockMvc.perform(post("/api/v1/objects/0900000180000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("renamed_document.pdf"));

        verify(objectService).updateObject(eq("session-123"), eq("0900000180000001"), any(UpdateObjectRequest.class));
    }

    // Tests for listFolderContents endpoint

    @Test
    void testListFolderContents_Success() throws Exception {
        List<ObjectInfo> contents = Arrays.asList(
                ObjectInfo.builder().objectId("obj1").type("dm_folder").name("Subfolder").build(),
                ObjectInfo.builder().objectId("obj2").type("dm_document").name("doc.pdf").build()
        );

        when(objectService.listFolderContents("session-123", "/Temp")).thenReturn(contents);

        mockMvc.perform(get("/api/v1/folders/Temp")
                        .param("sessionId", "session-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Subfolder"))
                .andExpect(jsonPath("$[1].name").value("doc.pdf"));
    }

    @Test
    void testListFolderContents_NotFound() throws Exception {
        when(objectService.listFolderContents("session-123", "/NonExistent"))
                .thenThrow(new ObjectNotFoundException("Folder not found: /NonExistent"));

        mockMvc.perform(get("/api/v1/folders/NonExistent")
                        .param("sessionId", "session-123"))
                .andExpect(status().isNotFound());
    }

    // Tests for listTypes endpoint

    @Test
    void testListTypes_Success() throws Exception {
        List<TypeInfo> types = Arrays.asList(
                TypeInfo.builder().name("dm_document").superType("dm_sysobject").build(),
                TypeInfo.builder().name("dm_folder").superType("dm_sysobject").build()
        );

        when(objectService.listTypes("session-123", null)).thenReturn(types);

        mockMvc.perform(get("/api/v1/types")
                        .param("sessionId", "session-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("dm_document"))
                .andExpect(jsonPath("$[1].name").value("dm_folder"));
    }

    @Test
    void testListTypes_WithPattern() throws Exception {
        List<TypeInfo> types = Arrays.asList(
                TypeInfo.builder().name("dm_document").build()
        );

        when(objectService.listTypes("session-123", "dm_doc*")).thenReturn(types);

        mockMvc.perform(get("/api/v1/types")
                        .param("sessionId", "session-123")
                        .param("pattern", "dm_doc*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // Tests for getTypeInfo endpoint

    @Test
    void testGetTypeInfo_Success() throws Exception {
        List<TypeInfo.AttributeInfo> attrs = Arrays.asList(
                TypeInfo.AttributeInfo.builder()
                        .name("object_name")
                        .dataType("STRING")
                        .length(255)
                        .repeating(false)
                        .build()
        );

        TypeInfo typeInfo = TypeInfo.builder()
                .name("dm_document")
                .superType("dm_sysobject")
                .systemType(false)
                .attributes(attrs)
                .build();

        when(objectService.getTypeInfo("session-123", "dm_document")).thenReturn(typeInfo);

        mockMvc.perform(get("/api/v1/types/dm_document")
                        .param("sessionId", "session-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("dm_document"))
                .andExpect(jsonPath("$.superType").value("dm_sysobject"))
                .andExpect(jsonPath("$.attributes[0].name").value("object_name"));
    }

    @Test
    void testGetTypeInfo_NotFound() throws Exception {
        when(objectService.getTypeInfo("session-123", "nonexistent_type"))
                .thenThrow(new ObjectNotFoundException("Type not found: nonexistent_type"));

        mockMvc.perform(get("/api/v1/types/nonexistent_type")
                        .param("sessionId", "session-123"))
                .andExpect(status().isNotFound());
    }

    // Tests for executeApi endpoint

    @Test
    void testExecuteApi_Success() throws Exception {
        ApiRequest request = ApiRequest.builder()
                .sessionId("session-123")
                .objectId("0900000180000001")
                .method("getObjectName")
                .build();

        ApiResponse response = ApiResponse.builder()
                .result("test_document.pdf")
                .resultType("String")
                .executionTimeMs(15)
                .build();

        when(objectService.executeApi(any(ApiRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("test_document.pdf"))
                .andExpect(jsonPath("$.resultType").value("String"));
    }

    @Test
    void testExecuteApi_ObjectNotFound() throws Exception {
        ApiRequest request = ApiRequest.builder()
                .sessionId("session-123")
                .objectId("nonexistent")
                .method("getObjectName")
                .build();

        when(objectService.executeApi(any(ApiRequest.class)))
                .thenThrow(new ObjectNotFoundException("nonexistent"));

        mockMvc.perform(post("/api/v1/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OBJECT_NOT_FOUND"));
    }

    @Test
    void testExecuteApi_WithArgs() throws Exception {
        ApiRequest request = ApiRequest.builder()
                .sessionId("session-123")
                .objectId("0900000180000001")
                .method("getString")
                .args(Arrays.asList("object_name"))
                .build();

        ApiResponse response = ApiResponse.builder()
                .result("test_document.pdf")
                .resultType("String")
                .executionTimeMs(10)
                .build();

        when(objectService.executeApi(any(ApiRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("test_document.pdf"));
    }

    @Test
    void testExecuteApi_OnType() throws Exception {
        ApiRequest request = ApiRequest.builder()
                .sessionId("session-123")
                .typeName("dm_document")
                .method("getName")
                .build();

        ApiResponse response = ApiResponse.builder()
                .result("dm_document")
                .resultType("String")
                .executionTimeMs(5)
                .build();

        when(objectService.executeApi(any(ApiRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("dm_document"));
    }

    // Tests for executeDmApi endpoint

    @Test
    void testExecuteDmApi_Get_Success() throws Exception {
        DmApiRequest request = DmApiRequest.builder()
                .sessionId("session-123")
                .apiType("get")
                .command("getservermap,session")
                .build();

        ApiResponse response = ApiResponse.builder()
                .result("docbase1:192.168.1.100:1489")
                .resultType("String")
                .executionTimeMs(25)
                .build();

        when(dmApiService.execute(any(DmApiRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/dmapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("docbase1:192.168.1.100:1489"))
                .andExpect(jsonPath("$.resultType").value("String"));

        verify(dmApiService).execute(any(DmApiRequest.class));
    }

    @Test
    void testExecuteDmApi_Exec_Success() throws Exception {
        DmApiRequest request = DmApiRequest.builder()
                .sessionId("session-123")
                .apiType("exec")
                .command("save,session,0900000180000001")
                .build();

        ApiResponse response = ApiResponse.builder()
                .result(true)
                .resultType("Boolean")
                .executionTimeMs(150)
                .build();

        when(dmApiService.execute(any(DmApiRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/dmapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.resultType").value("Boolean"));
    }

    @Test
    void testExecuteDmApi_Set_Success() throws Exception {
        DmApiRequest request = DmApiRequest.builder()
                .sessionId("session-123")
                .apiType("set")
                .command("set,session,0900000180000001,object_name,NewName")
                .build();

        ApiResponse response = ApiResponse.builder()
                .result(true)
                .resultType("Boolean")
                .executionTimeMs(50)
                .build();

        when(dmApiService.execute(any(DmApiRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/dmapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(true))
                .andExpect(jsonPath("$.resultType").value("Boolean"));
    }

    @Test
    void testExecuteDmApi_Dump_Success() throws Exception {
        DmApiRequest request = DmApiRequest.builder()
                .sessionId("session-123")
                .apiType("get")
                .command("dump,session,0900000180000001")
                .build();

        String dumpOutput = "USER ATTRIBUTES\n" +
                "  object_name              : test_document.pdf\n" +
                "  title                    : Test Document\n" +
                "SYSTEM ATTRIBUTES\n" +
                "  r_object_id              : 0900000180000001";

        ApiResponse response = ApiResponse.builder()
                .result(dumpOutput)
                .resultType("String")
                .executionTimeMs(100)
                .build();

        when(dmApiService.execute(any(DmApiRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/dmapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultType").value("String"));
    }

    @Test
    void testExecuteDmApi_MissingSessionId() throws Exception {
        DmApiRequest request = DmApiRequest.builder()
                .apiType("get")
                .command("getservermap,session")
                .build();

        mockMvc.perform(post("/api/v1/dmapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExecuteDmApi_MissingApiType() throws Exception {
        DmApiRequest request = DmApiRequest.builder()
                .sessionId("session-123")
                .command("getservermap,session")
                .build();

        mockMvc.perform(post("/api/v1/dmapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExecuteDmApi_MissingCommand() throws Exception {
        DmApiRequest request = DmApiRequest.builder()
                .sessionId("session-123")
                .apiType("get")
                .build();

        mockMvc.perform(post("/api/v1/dmapi")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}

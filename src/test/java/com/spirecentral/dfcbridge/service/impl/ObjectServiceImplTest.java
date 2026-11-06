package com.spirecentral.dfcbridge.service.impl;

import com.documentum.fc.client.DfIdNotFoundException;
import com.documentum.fc.client.DfQuery;
import com.documentum.fc.client.IDfCollection;
import com.documentum.fc.common.DfException;
import com.documentum.fc.common.DfId;
import com.documentum.fc.common.DfTime;
import com.documentum.fc.common.IDfId;
import com.documentum.fc.common.IDfTime;
import com.spirecentral.dfcbridge.dto.ApiRequest;
import com.spirecentral.dfcbridge.dto.ApiResponse;
import com.spirecentral.dfcbridge.dto.UpdateObjectRequest;
import com.spirecentral.dfcbridge.exception.DfcBridgeException;
import com.spirecentral.dfcbridge.exception.ObjectNotFoundException;
import com.spirecentral.dfcbridge.model.AttributeValue;
import com.spirecentral.dfcbridge.model.ObjectInfo;
import com.spirecentral.dfcbridge.model.TypeInfo;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import com.spirecentral.dfcbridge.util.DfcExceptionUtils;
import com.spirecentral.dfcbridge.util.RowCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ObjectServiceImpl.
 * Tests the reflection-based object retrieval including different object types
 * that have different name attributes (dm_group uses group_name, dm_user uses user_name).
 */
@ExtendWith(MockitoExtension.class)
class ObjectServiceImplTest {

    @Mock
    private DfcSessionService sessionService;

    private ObjectServiceImpl objectService;

    @BeforeEach
    void setUp() {
        objectService = new ObjectServiceImpl(sessionService);
    }

    @AfterEach
    void tearDown() {
        DfQuery.reset(null);
    }

    private void stubSession(Object dfSession) {
        when(sessionService.withSession(eq("test-session"), any())).thenAnswer(
                invocation -> invocation.<Function<Object, Object>>getArgument(1).apply(dfSession));
    }

    // ========== getObject tests for different object types ==========

    @Test
    void getObject_dmDocument_usesGetObjectName() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfSysObject mockObject = new MockDfSysObject();
        mockObject.setTypeName("dm_document");
        mockObject.setObjectName("test_document.pdf");
        mockObject.setPermit(6);
        mockSession.setObjectToReturn(mockObject);

        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        assertEquals("dm_document", result.getType());
        assertEquals("test_document.pdf", result.getName());
        assertEquals("0900000180000001", result.getObjectId());
    }

    @Test
    void getObject_dmFolder_usesGetObjectName() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfSysObject mockObject = new MockDfSysObject();
        mockObject.setTypeName("dm_folder");
        mockObject.setObjectName("Test Folder");
        mockObject.setPermit(7);
        mockSession.setObjectToReturn(mockObject);

        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0b00000180000001");

        // Assert
        assertNotNull(result);
        assertEquals("dm_folder", result.getType());
        assertEquals("Test Folder", result.getName());
    }

    @Test
    void getObject_dmGroup_usesGroupName() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfGroup mockGroup = new MockDfGroup();
        mockGroup.setTypeName("dm_group");
        mockGroup.setGroupName("administrators");
        mockGroup.setPermit(7);
        mockSession.setObjectToReturn(mockGroup);

        stubSession(mockSession);

        // Act - this would fail before the fix because dm_group doesn't have getObjectName
        ObjectInfo result = objectService.getObject("test-session", "1200000180000001");

        // Assert
        assertNotNull(result);
        assertEquals("dm_group", result.getType());
        assertEquals("administrators", result.getName());
    }

    @Test
    void getObject_dmUser_usesUserName() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfUser mockUser = new MockDfUser();
        mockUser.setTypeName("dm_user");
        mockUser.setUserName("jsmith");
        mockUser.setPermit(7);
        mockSession.setObjectToReturn(mockUser);

        stubSession(mockSession);

        // Act - this would fail before the fix because dm_user doesn't have getObjectName
        ObjectInfo result = objectService.getObject("test-session", "1100000180000001");

        // Assert
        assertNotNull(result);
        assertEquals("dm_user", result.getType());
        assertEquals("jsmith", result.getName());
    }

    @Test
    void getObject_dmFormat_usesNameAttribute() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfFormat mockFormat = new MockDfFormat();
        mockFormat.setTypeName("dm_format");
        mockFormat.setName("pdf");
        mockSession.setObjectToReturn(mockFormat);

        stubSession(mockSession);

        // Act - dm_format uses "name" attribute, not "object_name"
        ObjectInfo result = objectService.getObject("test-session", "2700000180000001");

        // Assert
        assertNotNull(result);
        assertEquals("dm_format", result.getType());
        assertEquals("pdf", result.getName());
        // dm_format doesn't have getPermit, so permissionLevel should be null
        assertNull(result.getPermissionLevel());
    }

    @Test
    void getObject_dmGroup_noPermissionLevel() {
        // Arrange - dm_group objects don't have getPermit() method
        MockDfSession mockSession = new MockDfSession();
        MockDfGroupNoPermit mockGroup = new MockDfGroupNoPermit();
        mockGroup.setTypeName("dm_group");
        mockGroup.setGroupName("docu");
        mockSession.setObjectToReturn(mockGroup);

        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "1200000180000100");

        // Assert
        assertNotNull(result);
        assertEquals("dm_group", result.getType());
        assertEquals("docu", result.getName());
        // Non-sysobject types don't have getPermit, so permissionLevel should be null
        assertNull(result.getPermissionLevel());
    }

    @Test
    void getObject_dmDocument_hasPermissionLevel() {
        // Arrange - dm_document (sysobject) has getPermit() method
        MockDfSession mockSession = new MockDfSession();
        MockDfSysObject mockObject = new MockDfSysObject();
        mockObject.setTypeName("dm_document");
        mockObject.setObjectName("test.pdf");
        mockObject.setPermit(7);
        mockSession.setObjectToReturn(mockObject);

        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        assertEquals("dm_document", result.getType());
        // Sysobjects have getPermit, so permissionLevel should be set
        assertEquals(7, result.getPermissionLevel());
        assertEquals("DELETE", result.getPermissionLabel());
    }

    // ========== Repeating Attributes Tests ==========

    @Test
    void getObject_withRepeatingStringAttribute_returnsAllValues() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfObjectWithAttributes mockObject = new MockDfObjectWithAttributes();
        mockObject.setTypeName("dm_document");
        mockObject.setPermit(7);

        // Add a repeating string attribute with multiple values
        mockObject.addAttribute("r_version_label", 2, true, "1.0", "CURRENT");
        mockObject.addAttribute("object_name", 2, false, "test.pdf");

        mockSession.setObjectToReturn(mockObject);
        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        assertNotNull(result.getAttributes());

        AttributeValue versionLabelsAttr = result.getAttributes().get("r_version_label");
        assertNotNull(versionLabelsAttr);
        assertEquals("string", versionLabelsAttr.getType());
        assertTrue(versionLabelsAttr.isRepeating());
        assertInstanceOf(List.class, versionLabelsAttr.getValue());

        @SuppressWarnings("unchecked")
        List<Object> labels = (List<Object>) versionLabelsAttr.getValue();
        assertEquals(2, labels.size());
        assertEquals("1.0", labels.get(0));
        assertEquals("CURRENT", labels.get(1));
    }

    @Test
    void getObject_withEmptyRepeatingAttribute_returnsEmptyList() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfObjectWithAttributes mockObject = new MockDfObjectWithAttributes();
        mockObject.setTypeName("dm_document");
        mockObject.setPermit(7);

        // Add an empty repeating attribute
        mockObject.addAttribute("keywords", 2, true); // no values
        mockObject.addAttribute("object_name", 2, false, "test.pdf");

        mockSession.setObjectToReturn(mockObject);
        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        AttributeValue keywordsAttr = result.getAttributes().get("keywords");
        assertNotNull(keywordsAttr);
        assertEquals("string", keywordsAttr.getType());
        assertTrue(keywordsAttr.isRepeating());
        assertInstanceOf(List.class, keywordsAttr.getValue());

        @SuppressWarnings("unchecked")
        List<Object> keywordList = (List<Object>) keywordsAttr.getValue();
        assertTrue(keywordList.isEmpty());
    }

    @Test
    void getObject_withSingleValueAttribute_returnsScalarWithType() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfObjectWithAttributes mockObject = new MockDfObjectWithAttributes();
        mockObject.setTypeName("dm_document");
        mockObject.setPermit(7);

        // Add single-value attributes of different types
        mockObject.addAttribute("object_name", 2, false, "test.pdf");      // STRING
        mockObject.addAttribute("r_content_size", 1, false, 12345);        // INTEGER
        mockObject.addAttribute("a_is_hidden", 0, false, false);           // BOOLEAN

        mockSession.setObjectToReturn(mockObject);
        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        Map<String, AttributeValue> attrs = result.getAttributes();

        // Check object_name
        AttributeValue nameAttr = attrs.get("object_name");
        assertNotNull(nameAttr);
        assertEquals("string", nameAttr.getType());
        assertFalse(nameAttr.isRepeating());
        assertEquals("test.pdf", nameAttr.getValue());

        // Check r_content_size
        AttributeValue sizeAttr = attrs.get("r_content_size");
        assertNotNull(sizeAttr);
        assertEquals("integer", sizeAttr.getType());
        assertFalse(sizeAttr.isRepeating());
        assertEquals(12345, sizeAttr.getValue());

        // Check a_is_hidden
        AttributeValue hiddenAttr = attrs.get("a_is_hidden");
        assertNotNull(hiddenAttr);
        assertEquals("boolean", hiddenAttr.getType());
        assertFalse(hiddenAttr.isRepeating());
        assertEquals(false, hiddenAttr.getValue());
    }

    @Test
    void getObject_withRepeatingIntegerAttribute_returnsAllValues() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfObjectWithAttributes mockObject = new MockDfObjectWithAttributes();
        mockObject.setTypeName("dm_document");
        mockObject.setPermit(7);

        // Add a repeating integer attribute
        mockObject.addAttribute("i_position", 1, true, 0, 1, 2);
        mockObject.addAttribute("object_name", 2, false, "test.pdf");

        mockSession.setObjectToReturn(mockObject);
        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        AttributeValue positionsAttr = result.getAttributes().get("i_position");
        assertNotNull(positionsAttr);
        assertEquals("integer", positionsAttr.getType());
        assertTrue(positionsAttr.isRepeating());
        assertInstanceOf(List.class, positionsAttr.getValue());

        @SuppressWarnings("unchecked")
        List<Object> positionList = (List<Object>) positionsAttr.getValue();
        assertEquals(3, positionList.size());
        assertEquals(0, positionList.get(0));
        assertEquals(1, positionList.get(1));
        assertEquals(2, positionList.get(2));
    }

    @Test
    void getObject_withMixedAttributes_handlesBothCorrectly() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfObjectWithAttributes mockObject = new MockDfObjectWithAttributes();
        mockObject.setTypeName("dm_document");
        mockObject.setPermit(7);

        // Mix of single and repeating attributes
        mockObject.addAttribute("object_name", 2, false, "report.pdf");
        mockObject.addAttribute("r_version_label", 2, true, "1.0", "1.1", "CURRENT");
        mockObject.addAttribute("title", 2, false, "Annual Report");
        mockObject.addAttribute("authors", 2, true, "John Doe", "Jane Smith");
        mockObject.addAttribute("r_content_size", 1, false, 98765);

        mockSession.setObjectToReturn(mockObject);
        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        Map<String, AttributeValue> attrs = result.getAttributes();

        // Single values should have type metadata and not be repeating
        assertEquals("report.pdf", attrs.get("object_name").getValue());
        assertFalse(attrs.get("object_name").isRepeating());
        assertEquals("string", attrs.get("object_name").getType());

        assertEquals("Annual Report", attrs.get("title").getValue());
        assertEquals(98765, attrs.get("r_content_size").getValue());
        assertEquals("integer", attrs.get("r_content_size").getType());

        // Repeating values should have type metadata and be marked as repeating
        AttributeValue versionLabelsAttr = attrs.get("r_version_label");
        assertTrue(versionLabelsAttr.isRepeating());
        assertEquals("string", versionLabelsAttr.getType());

        @SuppressWarnings("unchecked")
        List<Object> versionLabels = (List<Object>) versionLabelsAttr.getValue();
        assertEquals(3, versionLabels.size());
        assertEquals("1.0", versionLabels.get(0));
        assertEquals("1.1", versionLabels.get(1));
        assertEquals("CURRENT", versionLabels.get(2));

        AttributeValue authorsAttr = attrs.get("authors");
        assertTrue(authorsAttr.isRepeating());

        @SuppressWarnings("unchecked")
        List<Object> authors = (List<Object>) authorsAttr.getValue();
        assertEquals(2, authors.size());
        assertEquals("John Doe", authors.get(0));
        assertEquals("Jane Smith", authors.get(1));
    }

    // ========== ID Attribute Tests ==========

    @Test
    void getObject_withSingleIdAttribute_returnsAsString() {
        // Arrange - tests fix for issue where i_chronicle_id was returned as float
        MockDfSession mockSession = new MockDfSession();
        MockDfObjectWithAttributes mockObject = new MockDfObjectWithAttributes();
        mockObject.setTypeName("dm_document");
        mockObject.setPermit(7);

        // Add single ID attributes - these should be returned as strings, not numbers
        mockObject.addAttribute("object_name", 2, false, "test.pdf");
        mockObject.addAttribute("i_chronicle_id", 3, false, "0904719980000203"); // DM_ID = 3
        mockObject.addAttribute("i_cabinet_id", 3, false, "0c04719980000107"); // DM_ID = 3

        mockSession.setObjectToReturn(mockObject);
        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        Map<String, AttributeValue> attrs = result.getAttributes();

        // i_chronicle_id should be a string with leading zero preserved
        AttributeValue chronicleIdAttr = attrs.get("i_chronicle_id");
        assertNotNull(chronicleIdAttr);
        assertEquals("id", chronicleIdAttr.getType());
        assertFalse(chronicleIdAttr.isRepeating());
        assertEquals("0904719980000203", chronicleIdAttr.getValue());

        // i_cabinet_id should also be a string
        AttributeValue cabinetIdAttr = attrs.get("i_cabinet_id");
        assertNotNull(cabinetIdAttr);
        assertEquals("id", cabinetIdAttr.getType());
        assertEquals("0c04719980000107", cabinetIdAttr.getValue());
    }

    @Test
    void getObject_withRepeatingIdAttribute_returnsAsStringArray() {
        // Arrange - tests fix for missing i_folder_id repeating attribute
        MockDfSession mockSession = new MockDfSession();
        MockDfObjectWithAttributes mockObject = new MockDfObjectWithAttributes();
        mockObject.setTypeName("dm_document");
        mockObject.setPermit(7);

        // Add repeating ID attribute - should be returned as array of strings
        mockObject.addAttribute("object_name", 2, false, "test.pdf");
        mockObject.addAttribute("i_folder_id", 3, true, "0c04719980000107", "0c04719980000108", "0c04719980000109"); // DM_ID = 3

        mockSession.setObjectToReturn(mockObject);
        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        Map<String, AttributeValue> attrs = result.getAttributes();

        // i_folder_id should be a repeating ID attribute with string values
        AttributeValue folderIdAttr = attrs.get("i_folder_id");
        assertNotNull(folderIdAttr);
        assertEquals("id", folderIdAttr.getType());
        assertTrue(folderIdAttr.isRepeating());
        assertInstanceOf(List.class, folderIdAttr.getValue());

        @SuppressWarnings("unchecked")
        List<Object> folderIds = (List<Object>) folderIdAttr.getValue();
        assertEquals(3, folderIds.size());
        assertEquals("0c04719980000107", folderIds.get(0));
        assertEquals("0c04719980000108", folderIds.get(1));
        assertEquals("0c04719980000109", folderIds.get(2));
    }

    @Test
    void getObject_withEmptyRepeatingIdAttribute_returnsEmptyArray() {
        // Arrange
        MockDfSession mockSession = new MockDfSession();
        MockDfObjectWithAttributes mockObject = new MockDfObjectWithAttributes();
        mockObject.setTypeName("dm_document");
        mockObject.setPermit(7);

        // Add empty repeating ID attribute
        mockObject.addAttribute("object_name", 2, false, "test.pdf");
        mockObject.addAttribute("i_folder_id", 3, true); // no values - DM_ID = 3

        mockSession.setObjectToReturn(mockObject);
        stubSession(mockSession);

        // Act
        ObjectInfo result = objectService.getObject("test-session", "0900000180000001");

        // Assert
        assertNotNull(result);
        AttributeValue folderIdAttr = result.getAttributes().get("i_folder_id");
        assertNotNull(folderIdAttr);
        assertEquals("id", folderIdAttr.getType());
        assertTrue(folderIdAttr.isRepeating());

        @SuppressWarnings("unchecked")
        List<Object> folderIds = (List<Object>) folderIdAttr.getValue();
        assertTrue(folderIds.isEmpty());
    }

    // ========== executeApi argument conversion tests ==========

    @Test
    void executeApi_setTimeWithString_passesDfTime() {
        MockDfSysObject mockObject = new MockDfSysObject();
        stubSessionReturning(mockObject);

        objectService.executeApi(apiRequest("setTime", "a_last_review_date", "04/11/2026 18:30:00"));

        assertEquals("a_last_review_date", mockObject.getLastSetAttrName());
        IDfTime time = assertInstanceOf(IDfTime.class, mockObject.getLastSetValue());
        assertEquals("04/11/2026 18:30:00", time.toString());
    }

    @Test
    void executeApi_setIdWithString_passesDfId() {
        MockDfSysObject mockObject = new MockDfSysObject();
        stubSessionReturning(mockObject);

        objectService.executeApi(apiRequest("setId", "alias_set_id", "6604719980000101"));

        assertEquals("alias_set_id", mockObject.getLastSetAttrName());
        IDfId id = assertInstanceOf(IDfId.class, mockObject.getLastSetValue());
        assertEquals("6604719980000101", id.getId());
    }

    @Test
    void executeApi_setTimeWithInvalidTime_throwsApiErrorWithoutInvoking() {
        MockDfSysObject mockObject = new MockDfSysObject();
        stubSessionReturning(mockObject);
        ApiRequest request = apiRequest("setTime", "a_last_review_date", DfTime.INVALID_VALUE);

        DfcBridgeException e = assertThrows(DfcBridgeException.class, () -> objectService.executeApi(request));

        assertEquals("API_ERROR", e.getCode());
        assertNull(mockObject.getLastSetAttrName());
    }

    @Test
    void executeApi_integerForDoubleParam_callsMethod() {
        MockApiTarget target = new MockApiTarget();
        stubSessionReturning(target);

        objectService.executeApi(apiRequest("setDouble", "weight", 2));

        assertEquals(List.of("setDouble weight=2.0"), target.getCalls());
    }

    @Test
    void executeApi_doubleForIntParam_throwsApiErrorWithoutInvoking() {
        MockApiTarget target = new MockApiTarget();
        stubSessionReturning(target);
        ApiRequest request = apiRequest("setInt", "retention", 0.0);

        DfcBridgeException e = assertThrows(DfcBridgeException.class, () -> objectService.executeApi(request));

        assertEquals("API_ERROR", e.getCode());
        assertTrue(target.getCalls().isEmpty());
    }

    @Test
    void executeApi_stringArgWithStringAndIdOverloads_callsStringOverload() {
        MockApiTarget target = new MockApiTarget();
        stubSessionReturning(target);

        objectService.executeApi(apiRequest("getRelatedSession", "EDMS"));

        assertEquals(List.of("getRelatedSession(String)"), target.getCalls());
    }

    @Test
    void executeApi_unknownTypeName_throwsObjectNotFound() {
        stubSession(new MockDfSession());
        ApiRequest request = ApiRequest.builder()
                .sessionId("test-session")
                .typeName("no_such_type")
                .method("getName")
                .build();

        assertThrows(ObjectNotFoundException.class, () -> objectService.executeApi(request));
    }

    @Test
    void executeApi_idResult_returnsIdText() {
        stubSessionReturning(new MockApiTarget());

        ApiResponse response = objectService.executeApi(apiRequest("getObjectId"));

        assertEquals("0900000180000001", response.getResult());
        assertEquals("DfId", response.getResultType());
    }

    @Test
    void executeApi_longResult_returnsLong() {
        stubSessionReturning(new MockApiTarget());

        ApiResponse response = objectService.executeApi(apiRequest("getContentSize"));

        assertEquals(3000000000L, response.getResult());
    }

    @Test
    void executeApi_collectionResult_returnsTextAndClosesCollection() {
        MockApiTarget target = new MockApiTarget();
        stubSessionReturning(target);

        ApiResponse response = objectService.executeApi(apiRequest("getVersions"));

        assertEquals(target.getVersions().toString(), response.getResult());
        assertTrue(target.getVersions().isClosed());
    }

    // ========== updateObject attribute write tests ==========

    @Test
    void updateObject_nullValues_writeEmptyValueForDataType() {
        MockDfWritableObject object = writableObject();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("title", null);
        attributes.put("retention_days", null);
        attributes.put("weight", null);

        objectService.updateObject("test-session", "0900000180000001", updateRequest(attributes, true));

        assertTrue(object.getCalls().containsAll(List.of(
                "setString title=", "setString retention_days=0", "setString weight=0")));
    }

    @Test
    void updateObject_nullForRepeating_removesAllValues() {
        MockDfWritableObject object = writableObject();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("keywords", null);

        objectService.updateObject("test-session", "0900000180000001", updateRequest(attributes, true));

        assertEquals(List.of("removeAll keywords", "save"), object.getCalls());
    }

    @Test
    void updateObject_nullElementInList_writesEmptyValueAtIndex() {
        MockDfWritableObject object = writableObject();
        Map<String, Object> attributes = Map.of("keywords", Arrays.asList("first", null));

        objectService.updateObject("test-session", "0900000180000001", updateRequest(attributes, true));

        assertEquals(List.of("removeAll keywords", "setRepeatingString keywords[0]=first",
                "setRepeatingString keywords[1]=", "save"), object.getCalls());
    }

    @Test
    void updateObject_nonStringValues_writtenAsText() {
        MockDfWritableObject object = writableObject();
        Map<String, Object> attributes = Map.of(
                "subject", true,
                "retention_days", 5,
                "weight", 1.5,
                "content_bytes", 3000000000L);

        objectService.updateObject("test-session", "0900000180000001", updateRequest(attributes, true));

        assertTrue(object.getCalls().containsAll(List.of("setString subject=true",
                "setString retention_days=5", "setString weight=1.5", "setString content_bytes=3000000000")));
    }

    @Test
    void updateObject_objectValue_throwsUpdateError() {
        writableObject();
        UpdateObjectRequest request = updateRequest(Map.of("subject", Map.of("a", 1)), true);

        DfcBridgeException e = assertThrows(DfcBridgeException.class,
                () -> objectService.updateObject("test-session", "0900000180000001", request));

        assertEquals("UPDATE_ERROR", e.getCode());
    }

    @Test
    void updateObject_listForSingleValueAttribute_throwsUpdateError() {
        writableObject();
        UpdateObjectRequest request = updateRequest(Map.of("subject", List.of("a", "b")), true);

        DfcBridgeException e = assertThrows(DfcBridgeException.class,
                () -> objectService.updateObject("test-session", "0900000180000001", request));

        assertEquals("UPDATE_ERROR", e.getCode());
    }

    @Test
    void updateObject_saveFalse_revertsAndReturnsSetValues() {
        MockDfWritableObject object = writableObject();

        ObjectInfo result = objectService.updateObject("test-session", "0900000180000001",
                updateRequest(Map.of("title", "not saved"), false));

        assertEquals(List.of("setString title=not saved", "revert"), object.getCalls());
        assertEquals("not saved", result.getAttributes().get("title").getValue());
    }

    @Test
    void updateObject_setFails_revertsAndThrowsUpdateError() {
        MockDfWritableObject object = writableObject();
        object.failSetOn("subject");
        UpdateObjectRequest request = updateRequest(Map.of("subject", "x"), true);

        DfcBridgeException e = assertThrows(DfcBridgeException.class,
                () -> objectService.updateObject("test-session", "0900000180000001", request));

        assertEquals("UPDATE_ERROR", e.getCode());
        assertEquals(List.of("revert"), object.getCalls());
    }

    @Test
    void updateObject_setAndRevertFail_throwsOriginalError() {
        MockDfWritableObject object = writableObject();
        DfException setFailure = object.failSetOn("subject");
        object.failRevert();
        UpdateObjectRequest request = updateRequest(Map.of("subject", "x"), true);

        DfcBridgeException e = assertThrows(DfcBridgeException.class,
                () -> objectService.updateObject("test-session", "0900000180000001", request));

        assertEquals("UPDATE_ERROR", e.getCode());
        assertSame(setFailure, DfcExceptionUtils.unwrap((Exception) e.getCause()));
    }

    private MockDfWritableObject writableObject() {
        MockDfWritableObject object = new MockDfWritableObject();
        object.setTypeName("dm_document");
        object.setPermit(7);
        object.define("title", 2, false, "old title");
        object.define("subject", 2, false, "old subject");
        object.define("retention_days", 1, false, 10);
        object.define("content_bytes", 1, false, 0);
        object.define("weight", 5, false, 2.0);
        object.define("keywords", 2, true, "one", "two");
        stubSessionReturning(object);
        return object;
    }

    private UpdateObjectRequest updateRequest(Map<String, Object> attributes, boolean save) {
        return UpdateObjectRequest.builder()
                .sessionId("test-session")
                .attributes(attributes)
                .save(save)
                .build();
    }

    // ========== folder listing tests ==========

    private static final String FOLDER_QUERY = "SELECT r_object_id, object_name, r_object_type, a_content_type "
            + "FROM dm_sysobject WHERE FOLDER(ID('0b00000180000001')) ORDER BY object_name";

    @Test
    void listFolderContentsById_runsOneQueryOnFolderId() {
        stubSessionReturning(folder());
        DfQuery.reset(dql -> folderRows());

        List<ObjectInfo> contents = objectService.listFolderContentsById("test-session", "0b00000180000001");

        assertEquals(List.of(FOLDER_QUERY), DfQuery.executed);
        assertFolderRows(contents);
    }

    @Test
    void listFolderContentsById_objectIsNotFolder_throwsObjectNotFound() {
        stubSessionReturning(new Object());

        assertThrows(ObjectNotFoundException.class,
                () -> objectService.listFolderContentsById("test-session", "0900000180000003"));
        assertTrue(DfQuery.executed.isEmpty());
    }

    @Test
    void listFolderContents_runsOneQueryOnFolderId() {
        MockDfSession session = new MockDfSession();
        session.addFolder("/Temp/Reports", folder());
        stubSession(session);
        DfQuery.reset(dql -> folderRows());

        List<ObjectInfo> contents = objectService.listFolderContents("test-session", "/Temp/Reports");

        assertEquals(List.of(FOLDER_QUERY), DfQuery.executed);
        assertFolderRows(contents);
    }

    @Test
    void listFolderContents_noFolderAtPath_throwsObjectNotFound() {
        stubSession(new MockDfSession());

        assertThrows(ObjectNotFoundException.class,
                () -> objectService.listFolderContents("test-session", "/No/Such/Folder"));
    }

    private MockDfFolder folder() {
        return new MockDfFolder("0b00000180000001");
    }

    private RowCollection folderRows() {
        return new RowCollection(
                Map.of("r_object_id", "0b00000180000002", "object_name", "Archive",
                        "r_object_type", "dm_folder", "a_content_type", ""),
                Map.of("r_object_id", "0900000180000003", "object_name", "Report",
                        "r_object_type", "dm_document", "a_content_type", "pdf"));
    }

    private void assertFolderRows(List<ObjectInfo> contents) {
        assertEquals(2, contents.size());
        ObjectInfo document = contents.get(1);
        assertEquals("0900000180000003", document.getObjectId());
        assertEquals("dm_document", document.getType());
        assertEquals("Report", document.getName());
        assertEquals(Map.of("a_content_type", AttributeValue.single("string", "pdf")), document.getAttributes());
        assertNull(document.getPermissionLevel());
        assertEquals("Archive", contents.get(0).getName());
    }

    // ========== type list tests ==========

    @Test
    void listTypes_mapsNameAndSuperName() {
        stubSession(new MockDfSession());
        DfQuery.reset(dql -> new RowCollection(
                Map.of("name", "dm_document", "super_name", "dm_sysobject"),
                Map.of("name", "dmi_queue_item", "super_name", ""),
                Map.of("name", "my_contract", "super_name", "dm_document")));

        List<TypeInfo> types = objectService.listTypes("test-session", null);

        assertEquals(List.of("SELECT name, super_name FROM dm_type ORDER BY name"), DfQuery.executed);
        assertEquals("dm_document", types.get(0).getName());
        assertEquals("dm_sysobject", types.get(0).getSuperType());
        assertTrue(types.get(0).isSystemType());
        assertEquals("", types.get(1).getSuperType());
        assertTrue(types.get(1).isSystemType());
        assertFalse(types.get(2).isSystemType());
        assertNull(types.get(2).getAttributes());
    }

    @Test
    void listTypes_dmPattern_escapesUnderscore() {
        stubSession(new MockDfSession());
        DfQuery.reset(dql -> new RowCollection());

        objectService.listTypes("test-session", "dm_*");

        assertEquals(List.of("SELECT name, super_name FROM dm_type WHERE name LIKE 'dm\\_%' ESCAPE '\\' ORDER BY name"),
                DfQuery.executed);
    }

    // ========== missing object tests ==========

    @Test
    void getObject_idDoesNotExist_throwsObjectNotFound() {
        stubSessionThrowingIdNotFound();

        assertThrows(ObjectNotFoundException.class,
                () -> objectService.getObject("test-session", "0900000000000001"));
    }

    @Test
    void deleteObject_idDoesNotExist_throwsObjectNotFound() {
        stubSessionThrowingIdNotFound();

        assertThrows(ObjectNotFoundException.class,
                () -> objectService.deleteObject("test-session", "0900000000000001", false));
    }

    private void stubSessionThrowingIdNotFound() {
        MockDfSession mockSession = new MockDfSession();
        mockSession.setGetObjectException(new DfIdNotFoundException("DM_API_E_EXIST"));
        stubSession(mockSession);
    }

    private void stubSessionReturning(Object object) {
        MockDfSession mockSession = new MockDfSession();
        mockSession.setObjectToReturn(object);
        stubSession(mockSession);
    }

    private ApiRequest apiRequest(String method, Object... args) {
        return ApiRequest.builder()
                .sessionId("test-session")
                .objectId("0900000180000001")
                .method(method)
                .args(List.of(args))
                .build();
    }

    // ========== Mock DFC Objects ==========

    /**
     * Mock session that can return objects via getObject.
     */
    public static class MockDfSession implements com.documentum.fc.client.IDfSession {
        private Object objectToReturn;
        private DfException getObjectException;
        private final Map<String, Object> folders = new HashMap<>();

        public void setObjectToReturn(Object obj) {
            this.objectToReturn = obj;
        }

        public void addFolder(String path, Object folder) {
            folders.put(path, folder);
        }

        public void setGetObjectException(DfException e) {
            this.getObjectException = e;
        }

        public Object getObject(com.documentum.fc.common.IDfId id) throws DfException {
            if (getObjectException != null) {
                throw getObjectException;
            }
            return objectToReturn;
        }

        @Override
        public Object getType(String typeName) {
            return null;
        }

        @Override
        public Object getFolderByPath(String folderPath) {
            return folders.get(folderPath);
        }

        @Override
        public boolean isConnected() {
            return true;
        }

        @Override
        public String apiGet(String method, String args) {
            return null;
        }

        @Override
        public boolean apiExec(String method, String args) {
            return false;
        }

        @Override
        public boolean apiSet(String method, String args, String value) {
            return false;
        }
    }

    /**
     * Mock sysobject for dm_document, dm_folder, etc. that have getObjectName.
     */
    public static class MockDfSysObject implements MockDfPersistentObject {
        private String typeName;
        private String objectName;
        private int permit;
        private int attrCount = 0;
        private String lastSetAttrName;
        private Object lastSetValue;

        public void setTypeName(String typeName) {
            this.typeName = typeName;
        }

        public void setObjectName(String objectName) {
            this.objectName = objectName;
        }

        public void setTime(String attrName, IDfTime value) {
            lastSetAttrName = attrName;
            lastSetValue = value;
        }

        public void setId(String attrName, IDfId value) {
            lastSetAttrName = attrName;
            lastSetValue = value;
        }

        public String getLastSetAttrName() {
            return lastSetAttrName;
        }

        public Object getLastSetValue() {
            return lastSetValue;
        }

        public void setPermit(int permit) {
            this.permit = permit;
        }

        @Override
        public String getTypeName() {
            return typeName;
        }

        public String getObjectName() {
            return objectName;
        }

        @Override
        public int getPermit() {
            return permit;
        }

        @Override
        public int getAttrCount() {
            return attrCount;
        }

        @Override
        public String getString(String attrName) {
            if ("object_name".equals(attrName)) {
                return objectName;
            }
            return null;
        }

        @Override
        public boolean hasAttr(String attrName) {
            return "object_name".equals(attrName);
        }
    }

    /**
     * Mock dm_group object that uses group_name instead of object_name.
     */
    public static class MockDfGroup implements MockDfPersistentObject {
        private String typeName;
        private String groupName;
        private int permit;
        private int attrCount = 0;

        public void setTypeName(String typeName) {
            this.typeName = typeName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public void setPermit(int permit) {
            this.permit = permit;
        }

        @Override
        public String getTypeName() {
            return typeName;
        }

        // dm_group does NOT have getObjectName - that's the bug we're fixing!

        @Override
        public int getPermit() {
            return permit;
        }

        @Override
        public int getAttrCount() {
            return attrCount;
        }

        @Override
        public String getString(String attrName) {
            if ("group_name".equals(attrName)) {
                return groupName;
            }
            return null;
        }

        @Override
        public boolean hasAttr(String attrName) {
            return "group_name".equals(attrName);
        }
    }

    /**
     * Mock dm_user object that uses user_name instead of object_name.
     */
    public static class MockDfUser implements MockDfPersistentObject {
        private String typeName;
        private String userName;
        private int permit;
        private int attrCount = 0;

        public void setTypeName(String typeName) {
            this.typeName = typeName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public void setPermit(int permit) {
            this.permit = permit;
        }

        @Override
        public String getTypeName() {
            return typeName;
        }

        // dm_user does NOT have getObjectName - that's the bug we're fixing!

        @Override
        public int getPermit() {
            return permit;
        }

        @Override
        public int getAttrCount() {
            return attrCount;
        }

        @Override
        public String getString(String attrName) {
            if ("user_name".equals(attrName)) {
                return userName;
            }
            return null;
        }

        @Override
        public boolean hasAttr(String attrName) {
            return "user_name".equals(attrName);
        }
    }

    /**
     * Mock dm_format object that uses "name" attribute instead of "object_name".
     * dm_format is NOT a sysobject, so it doesn't have getPermit().
     */
    public static class MockDfFormat implements MockDfPersistentObjectNoPermit {
        private String typeName;
        private String name;
        private int attrCount = 0;

        public void setTypeName(String typeName) {
            this.typeName = typeName;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String getTypeName() {
            return typeName;
        }

        // No getPermit() - dm_format is not a sysobject

        @Override
        public int getAttrCount() {
            return attrCount;
        }

        @Override
        public String getString(String attrName) {
            if ("name".equals(attrName)) {
                return name;
            }
            return null;
        }

        @Override
        public boolean hasAttr(String attrName) {
            return "name".equals(attrName);
        }
    }

    /**
     * Mock dm_group that throws exception on getPermit() - simulates real DFC behaviour.
     * Non-sysobject types don't have the getPermit method.
     */
    public static class MockDfGroupNoPermit implements MockDfPersistentObjectNoPermit {
        private String typeName;
        private String groupName;
        private int attrCount = 0;

        public void setTypeName(String typeName) {
            this.typeName = typeName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        @Override
        public String getTypeName() {
            return typeName;
        }

        // No getPermit() method - this simulates real dm_group behaviour

        @Override
        public int getAttrCount() {
            return attrCount;
        }

        @Override
        public String getString(String attrName) {
            if ("group_name".equals(attrName)) {
                return groupName;
            }
            return null;
        }

        @Override
        public boolean hasAttr(String attrName) {
            return "group_name".equals(attrName);
        }
    }

    /**
     * Common interface for mock persistent objects.
     */
    public interface MockDfPersistentObject {
        String getTypeName();
        int getPermit();
        int getAttrCount();
        String getString(String attrName);
        boolean hasAttr(String attrName);
    }

    /**
     * Interface for mock objects that don't have getPermit (non-sysobjects).
     */
    public interface MockDfPersistentObjectNoPermit {
        String getTypeName();
        int getAttrCount();
        String getString(String attrName);
        boolean hasAttr(String attrName);
    }

    /**
     * Mock object that supports configurable attributes for testing attribute extraction.
     * Supports both single-value and repeating attributes of various types.
     */
    public static class MockDfObjectWithAttributes implements MockDfPersistentObject {
        private String typeName;
        private int permit;
        private final List<MockAttribute> attributes = new ArrayList<>();

        public void setTypeName(String typeName) {
            this.typeName = typeName;
        }

        public void setPermit(int permit) {
            this.permit = permit;
        }

        /**
         * Add an attribute with values.
         * @param name attribute name
         * @param dataType DFC constants: 0=BOOLEAN, 1=INTEGER, 2=STRING, 3=ID, 4=TIME, 5=DOUBLE
         * @param repeating whether the attribute is repeating
         * @param values the values (for repeating, can be multiple; for single, just one)
         */
        public void addAttribute(String name, int dataType, boolean repeating, Object... values) {
            attributes.add(new MockAttribute(name, dataType, repeating, values));
        }

        @Override
        public String getTypeName() {
            return typeName;
        }

        @Override
        public int getPermit() {
            return permit;
        }

        @Override
        public int getAttrCount() {
            return attributes.size();
        }

        public MockAttribute getAttr(int index) {
            return attributes.get(index);
        }

        @Override
        public String getString(String attrName) {
            for (MockAttribute attr : attributes) {
                // Support both STRING (2) and ID (3) data types
                // ID attributes use getString to preserve leading zeros
                if (attr.getName().equals(attrName) && (attr.getDataType() == 2 || attr.getDataType() == 3)) {
                    Object[] values = attr.getValues();
                    return values.length > 0 ? (String) values[0] : null;
                }
            }
            return null;
        }

        public int getInt(String attrName) {
            for (MockAttribute attr : attributes) {
                if (attr.getName().equals(attrName) && attr.getDataType() == 1) {
                    Object[] values = attr.getValues();
                    return values.length > 0 ? (Integer) values[0] : 0;
                }
            }
            return 0;
        }

        public boolean getBoolean(String attrName) {
            for (MockAttribute attr : attributes) {
                if (attr.getName().equals(attrName) && attr.getDataType() == 0) {
                    Object[] values = attr.getValues();
                    return values.length > 0 && (Boolean) values[0];
                }
            }
            return false;
        }

        public double getDouble(String attrName) {
            for (MockAttribute attr : attributes) {
                if (attr.getName().equals(attrName) && attr.getDataType() == 5) { // DM_DOUBLE = 5
                    Object[] values = attr.getValues();
                    return values.length > 0 ? (Double) values[0] : 0.0;
                }
            }
            return 0.0;
        }

        public int getValueCount(String attrName) {
            for (MockAttribute attr : attributes) {
                if (attr.getName().equals(attrName)) {
                    return attr.getValues().length;
                }
            }
            return 0;
        }

        public String getRepeatingString(String attrName, int index) {
            for (MockAttribute attr : attributes) {
                // Support both STRING (2) and ID (3) data types
                // ID attributes use getRepeatingString to preserve leading zeros
                if (attr.getName().equals(attrName) && (attr.getDataType() == 2 || attr.getDataType() == 3)) {
                    Object[] values = attr.getValues();
                    return index < values.length ? (String) values[index] : null;
                }
            }
            return null;
        }

        public int getRepeatingInt(String attrName, int index) {
            for (MockAttribute attr : attributes) {
                if (attr.getName().equals(attrName) && attr.getDataType() == 1) {
                    Object[] values = attr.getValues();
                    return index < values.length ? (Integer) values[index] : 0;
                }
            }
            return 0;
        }

        public boolean getRepeatingBoolean(String attrName, int index) {
            for (MockAttribute attr : attributes) {
                if (attr.getName().equals(attrName) && attr.getDataType() == 0) {
                    Object[] values = attr.getValues();
                    return index < values.length && (Boolean) values[index];
                }
            }
            return false;
        }

        public double getRepeatingDouble(String attrName, int index) {
            for (MockAttribute attr : attributes) {
                if (attr.getName().equals(attrName) && attr.getDataType() == 3) {
                    Object[] values = attr.getValues();
                    return index < values.length ? (Double) values[index] : 0.0;
                }
            }
            return 0.0;
        }

        @Override
        public boolean hasAttr(String attrName) {
            for (MockAttribute attr : attributes) {
                if (attr.getName().equals(attrName)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Mock object that records each write, with a type definition for the attribute data types.
     */
    public static class MockDfWritableObject extends MockDfObjectWithAttributes {
        private final List<MockAttribute> typeAttrs = new ArrayList<>();
        private final Map<String, String> setValues = new HashMap<>();
        private final List<String> calls = new ArrayList<>();
        private String failingAttr;
        private DfException setFailure;
        private boolean revertFails;

        public void define(String name, int dataType, boolean repeating, Object... values) {
            addAttribute(name, dataType, repeating, values);
            typeAttrs.add(new MockAttribute(name, dataType, repeating));
        }

        public DfException failSetOn(String attrName) {
            failingAttr = attrName;
            setFailure = new DfException("DM_API_E_BADATTRVALUE");
            return setFailure;
        }

        public void failRevert() {
            revertFails = true;
        }

        public List<String> getCalls() {
            return calls;
        }

        public MockTypeSession getSession() {
            return new MockTypeSession(new MockType(typeAttrs));
        }

        public boolean isAttrRepeating(String attrName) {
            return typeAttrs.stream().anyMatch(a -> a.getName().equals(attrName) && a.isRepeating());
        }

        public void setString(String attrName, String value) throws DfException {
            if (attrName.equals(failingAttr)) {
                throw setFailure;
            }
            calls.add("setString " + attrName + "=" + value);
            setValues.put(attrName, value);
        }

        public void setRepeatingString(String attrName, int index, String value) {
            calls.add("setRepeatingString " + attrName + "[" + index + "]=" + value);
        }

        public void removeAll(String attrName) {
            calls.add("removeAll " + attrName);
        }

        public void save() {
            calls.add("save");
        }

        public void revert() throws DfException {
            calls.add("revert");
            if (revertFails) {
                throw new DfException("DM_API_E_REVERT");
            }
        }

        @Override
        public String getString(String attrName) {
            return setValues.containsKey(attrName) ? setValues.get(attrName) : super.getString(attrName);
        }
    }

    public static class MockTypeSession {
        private final MockType type;

        public MockTypeSession(MockType type) {
            this.type = type;
        }

        public MockType getType(String typeName) {
            return type;
        }
    }

    public static class MockType {
        private final List<MockAttribute> attrs;

        public MockType(List<MockAttribute> attrs) {
            this.attrs = attrs;
        }

        public int getTypeAttrCount() {
            return attrs.size();
        }

        public MockAttribute getTypeAttr(int index) {
            return attrs.get(index);
        }
    }

    public static class MockDfFolder implements com.documentum.fc.client.IDfFolder {
        private final String objectId;

        public MockDfFolder(String objectId) {
            this.objectId = objectId;
        }

        @Override
        public String getString(String attrName) {
            return "r_object_id".equals(attrName) ? objectId : null;
        }
    }

    /**
     * Mock target for API calls, recording which method ran.
     */
    public static class MockApiTarget {
        private final List<String> calls = new ArrayList<>();
        private final MockCollection versions = new MockCollection();

        public List<String> getCalls() {
            return calls;
        }

        public void setDouble(String attrName, double value) {
            calls.add("setDouble " + attrName + "=" + value);
        }

        public void setInt(String attrName, int value) {
            calls.add("setInt " + attrName + "=" + value);
        }

        public String getRelatedSession(String repository) {
            calls.add("getRelatedSession(String)");
            return repository;
        }

        public String getRelatedSession(IDfId id) {
            calls.add("getRelatedSession(IDfId)");
            return id.getId();
        }

        public IDfId getObjectId() {
            return new DfId("0900000180000001");
        }

        public long getContentSize() {
            return 3000000000L;
        }

        public MockCollection getVersions() {
            return versions;
        }
    }

    public static class MockCollection implements IDfCollection {
        private boolean closed;

        @Override
        public boolean next() {
            return false;
        }

        @Override
        public void close() {
            closed = true;
        }

        public boolean isClosed() {
            return closed;
        }

        @Override
        public String toString() {
            return "versions collection";
        }
    }

    /**
     * Mock attribute for testing attribute extraction.
     */
    public static class MockAttribute {
        private final String name;
        private final int dataType;
        private final boolean repeating;
        private final Object[] values;

        public MockAttribute(String name, int dataType, boolean repeating, Object... values) {
            this.name = name;
            this.dataType = dataType;
            this.repeating = repeating;
            this.values = values != null ? values : new Object[0];
        }

        public String getName() {
            return name;
        }

        public int getDataType() {
            return dataType;
        }

        public boolean isRepeating() {
            return repeating;
        }

        public Object[] getValues() {
            return values;
        }
    }
}

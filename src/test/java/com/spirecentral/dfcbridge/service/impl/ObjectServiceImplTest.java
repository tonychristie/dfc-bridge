package com.spirecentral.dfcbridge.service.impl;

import com.spirecentral.dfcbridge.model.AttributeValue;
import com.spirecentral.dfcbridge.model.ObjectInfo;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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

        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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

        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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

        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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

        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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

        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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

        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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
        when(sessionService.getDfcSession("test-session")).thenReturn(mockSession);

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

    // ========== Mock DFC Objects ==========

    /**
     * Mock session that can return objects via getObject.
     */
    public static class MockDfSession implements com.documentum.fc.client.IDfSession {
        private Object objectToReturn;

        public void setObjectToReturn(Object obj) {
            this.objectToReturn = obj;
        }

        public Object getObject(com.documentum.fc.common.IDfId id) {
            return objectToReturn;
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

        public void setTypeName(String typeName) {
            this.typeName = typeName;
        }

        public void setObjectName(String objectName) {
            this.objectName = objectName;
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

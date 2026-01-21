package com.spirecentral.dfcbridge.service.impl;

import com.spirecentral.dfcbridge.model.ObjectInfo;
import com.spirecentral.dfcbridge.service.DfcSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}

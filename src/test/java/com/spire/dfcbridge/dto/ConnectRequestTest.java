package com.spire.dfcbridge.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void testValidRequest() {
        ConnectRequest request = ConnectRequest.builder()
                .docbroker("docbroker.example.com")
                .port(1489)
                .repository("TestRepo")
                .username("dmadmin")
                .password("password123")
                .build();

        var violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }

    @Test
    void testDefaultPort() {
        ConnectRequest request = ConnectRequest.builder()
                .docbroker("docbroker.example.com")
                .repository("TestRepo")
                .username("dmadmin")
                .password("password123")
                .build();

        assertEquals(1489, request.getPort());
    }

    @Test
    void testMissingDocbroker() {
        ConnectRequest request = ConnectRequest.builder()
                .port(1489)
                .repository("TestRepo")
                .username("dmadmin")
                .password("password123")
                .build();

        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("docbroker")));
    }

    @Test
    void testMissingRepository() {
        ConnectRequest request = ConnectRequest.builder()
                .docbroker("docbroker.example.com")
                .port(1489)
                .username("dmadmin")
                .password("password123")
                .build();

        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("repository")));
    }

    @Test
    void testMissingUsername() {
        ConnectRequest request = ConnectRequest.builder()
                .docbroker("docbroker.example.com")
                .port(1489)
                .repository("TestRepo")
                .password("password123")
                .build();

        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void testMissingPassword() {
        ConnectRequest request = ConnectRequest.builder()
                .docbroker("docbroker.example.com")
                .port(1489)
                .repository("TestRepo")
                .username("dmadmin")
                .build();

        var violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void testOptionalDomain() {
        ConnectRequest request = ConnectRequest.builder()
                .docbroker("docbroker.example.com")
                .port(1489)
                .repository("TestRepo")
                .username("dmadmin")
                .password("password123")
                .domain("MYDOMAIN")
                .build();

        assertEquals("MYDOMAIN", request.getDomain());
        var violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }
}

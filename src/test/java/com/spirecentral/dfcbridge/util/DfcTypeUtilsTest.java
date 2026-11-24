package com.spirecentral.dfcbridge.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class DfcTypeUtilsTest {

    // Tests for dataTypeToString

    @ParameterizedTest
    @CsvSource({
            "0, BOOLEAN",
            "1, INTEGER",
            "2, STRING",
            "3, ID",
            "4, TIME",
            "5, DOUBLE",
            "6, UNDEFINED",
            "-1, UNDEFINED",
            "100, UNDEFINED"
    })
    void testDataTypeToString(int dataType, String expected) {
        assertEquals(expected, DfcTypeUtils.dataTypeToString(dataType));
    }

    // Tests for getterName and repeatingGetterName

    @ParameterizedTest
    @CsvSource({
            "0, getBoolean",
            "1, getInt",
            "2, getString",
            "3, getString",
            "4, getTime",
            "5, getDouble",
            "6, getString"
    })
    void testGetterName(int dataType, String expected) {
        assertEquals(expected, DfcTypeUtils.getterName(dataType));
    }

    @ParameterizedTest
    @CsvSource({
            "0, getRepeatingBoolean",
            "1, getRepeatingInt",
            "2, getRepeatingString",
            "3, getRepeatingString",
            "4, getRepeatingTime",
            "5, getRepeatingDouble",
            "6, getRepeatingString"
    })
    void testRepeatingGetterName(int dataType, String expected) {
        assertEquals(expected, DfcTypeUtils.repeatingGetterName(dataType));
    }

    // Tests for toLikePattern

    @Test
    void testToLikePattern_UnderscoreMatchesItself() {
        assertEquals("dm\\_%", DfcTypeUtils.toLikePattern("dm_*"));
    }

    @Test
    void testToLikePattern_PercentMatchesItself() {
        assertEquals("50\\%%", DfcTypeUtils.toLikePattern("50%*"));
    }

    @Test
    void testToLikePattern_BackslashEscaped() {
        assertEquals("a\\\\b", DfcTypeUtils.toLikePattern("a\\b"));
    }

    @Test
    void testToLikePattern_SingleQuoteDoubled() {
        assertEquals("o''brien", DfcTypeUtils.toLikePattern("o'brien"));
    }

    // Tests for permitToLabel

    @ParameterizedTest
    @CsvSource({
            "1, NONE",
            "2, BROWSE",
            "3, READ",
            "4, RELATE",
            "5, VERSION",
            "6, WRITE",
            "7, DELETE",
            "0, UNKNOWN",
            "8, UNKNOWN",
            "-1, UNKNOWN"
    })
    void testPermitToLabel(int permit, String expected) {
        assertEquals(expected, DfcTypeUtils.permitToLabel(permit));
    }

    // Tests for sanitizeDqlString

    @ParameterizedTest
    @CsvSource({
            "test, test",
            "test's value, test''s value",
            "it's a 'test', it''s a ''test''",
            "no quotes, no quotes",
    })
    void testSanitizeDqlString(String input, String expected) {
        assertEquals(expected, DfcTypeUtils.sanitizeDqlString(input));
    }

    @Test
    void testSanitizeDqlString_QuotedString() {
        // Test string that is wrapped in single quotes
        // (Cannot use CsvSource as it interprets outer quotes as CSV delimiters)
        String input = "'quoted'";
        String expected = "''quoted''";
        assertEquals(expected, DfcTypeUtils.sanitizeDqlString(input));
    }

    @Test
    void testSanitizeDqlString_Null() {
        assertNull(DfcTypeUtils.sanitizeDqlString(null));
    }

    @Test
    void testSanitizeDqlString_Empty() {
        assertEquals("", DfcTypeUtils.sanitizeDqlString(""));
    }

    @Test
    void testSanitizeDqlString_SingleQuoteOnly() {
        assertEquals("''", DfcTypeUtils.sanitizeDqlString("'"));
    }

    @Test
    void testSanitizeDqlString_SqlInjectionAttempt() {
        // Attempt to inject: '; DROP TABLE dm_type; --
        String malicious = "'; DROP TABLE dm_type; --";
        String sanitized = DfcTypeUtils.sanitizeDqlString(malicious);

        // Should escape the single quote
        assertEquals("''; DROP TABLE dm_type; --", sanitized);
    }

    @Test
    void testSanitizeDqlString_MultipleQuotes() {
        String input = "test'''value";
        String expected = "test''''''value";
        assertEquals(expected, DfcTypeUtils.sanitizeDqlString(input));
    }

    @Test
    void testSanitizeDqlString_UnicodeCharacters() {
        String input = "测试'文档";
        String expected = "测试''文档";
        assertEquals(expected, DfcTypeUtils.sanitizeDqlString(input));
    }
}

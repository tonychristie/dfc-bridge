package com.spirecentral.dfcbridge.util;

/**
 * Utility class for DFC type conversions.
 */
public final class DfcTypeUtils {

    private DfcTypeUtils() {
        // Utility class
    }

    /**
     * Converts a DFC data type constant to its string representation.
     *
     * @param dataType the DFC data type constant
     * @return the string representation
     */
    public static String dataTypeToString(int dataType) {
        return switch (dataType) {
            case 0 -> "BOOLEAN";
            case 1 -> "INTEGER";
            case 2 -> "STRING";
            case 3 -> "ID";
            case 4 -> "TIME";
            case 5 -> "DOUBLE";
            default -> "UNDEFINED";
        };
    }

    /**
     * Converts a DFC permission level to its label.
     *
     * @param permit the DFC permission level
     * @return the permission label
     */
    public static String permitToLabel(int permit) {
        return switch (permit) {
            case 1 -> "NONE";
            case 2 -> "BROWSE";
            case 3 -> "READ";
            case 4 -> "RELATE";
            case 5 -> "VERSION";
            case 6 -> "WRITE";
            case 7 -> "DELETE";
            default -> "UNKNOWN";
        };
    }

    /**
     * Sanitizes a string for use in DQL queries by escaping single quotes.
     *
     * @param input the input string to sanitize
     * @return the sanitized string safe for DQL
     */
    public static String sanitizeDqlString(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("'", "''");
    }
}

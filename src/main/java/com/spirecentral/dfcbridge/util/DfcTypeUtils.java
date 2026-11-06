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
     * Returns the name of the DFC getter that reads a single value of a data type.
     *
     * @param dataType the DFC data type constant
     * @return the getter name, getString for an unknown type
     */
    public static String getterName(int dataType) {
        return switch (dataType) {
            case 0 -> "getBoolean";
            case 1 -> "getInt";
            case 3 -> "getId";
            case 4 -> "getTime";
            case 5 -> "getDouble";
            default -> "getString";
        };
    }

    /**
     * Returns the name of the DFC getter that reads one value of a repeating attribute of a data type.
     *
     * @param dataType the DFC data type constant
     * @return the getter name, getRepeatingString for an unknown type
     */
    public static String repeatingGetterName(int dataType) {
        return switch (dataType) {
            case 0 -> "getRepeatingBoolean";
            case 1 -> "getRepeatingInt";
            case 3 -> "getRepeatingId";
            case 4 -> "getRepeatingTime";
            case 5 -> "getRepeatingDouble";
            default -> "getRepeatingString";
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

    /**
     * Converts a name pattern to the text of a DQL LIKE clause that uses ESCAPE '\'.
     * Only * is a wildcard; _ and % match themselves.
     *
     * @param pattern the name pattern
     * @return the LIKE text, safe for DQL
     */
    public static String toLikePattern(String pattern) {
        // \ is escaped first so that a \ in the pattern cannot escape the next character
        return sanitizeDqlString(pattern)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("*", "%");
    }
}

package com.ibm.codeanalyzer.util;

import java.util.regex.Pattern;

/**
 * String utility methods for code analysis.
 */
public class StringUtils {

    private static final Pattern CAMEL_CASE_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
    private static final Pattern PASCAL_CASE_PATTERN = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");
    private static final Pattern SNAKE_CASE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern SCREAMING_SNAKE_CASE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private StringUtils() {
        // Utility class
    }

    /**
     * Check if a string is null or empty.
     *
     * @param str String to check
     * @return true if null or empty
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * Check if a string is null, empty, or contains only whitespace.
     *
     * @param str String to check
     * @return true if blank
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Truncate a string to a maximum length.
     *
     * @param str String to truncate
     * @param maxLength Maximum length
     * @return Truncated string with "..." if truncated
     */
    public static String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * Check if a string follows camelCase naming convention.
     *
     * @param str String to check
     * @return true if camelCase
     */
    public static boolean isCamelCase(String str) {
        return str != null && CAMEL_CASE_PATTERN.matcher(str).matches();
    }

    /**
     * Check if a string follows PascalCase naming convention.
     *
     * @param str String to check
     * @return true if PascalCase
     */
    public static boolean isPascalCase(String str) {
        return str != null && PASCAL_CASE_PATTERN.matcher(str).matches();
    }

    /**
     * Check if a string follows snake_case naming convention.
     *
     * @param str String to check
     * @return true if snake_case
     */
    public static boolean isSnakeCase(String str) {
        return str != null && SNAKE_CASE_PATTERN.matcher(str).matches();
    }

    /**
     * Check if a string follows SCREAMING_SNAKE_CASE naming convention.
     *
     * @param str String to check
     * @return true if SCREAMING_SNAKE_CASE
     */
    public static boolean isScreamingSnakeCase(String str) {
        return str != null && SCREAMING_SNAKE_CASE_PATTERN.matcher(str).matches();
    }

    /**
     * Convert camelCase to snake_case.
     *
     * @param str String in camelCase
     * @return String in snake_case
     */
    public static String camelToSnake(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Convert snake_case to camelCase.
     *
     * @param str String in snake_case
     * @return String in camelCase
     */
    public static String snakeToCamel(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : str.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else {
                result.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
        }
        return result.toString();
    }

    /**
     * Remove all whitespace from a string.
     *
     * @param str String to process
     * @return String without whitespace
     */
    public static String removeWhitespace(String str) {
        if (str == null) {
            return null;
        }
        return str.replaceAll("\\s+", "");
    }

    /**
     * Count occurrences of a substring in a string.
     *
     * @param str String to search
     * @param substring Substring to count
     * @return Number of occurrences
     */
    public static int countOccurrences(String str, String substring) {
        if (str == null || substring == null || substring.isEmpty()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    /**
     * Escape special characters for regex.
     *
     * @param str String to escape
     * @return Escaped string
     */
    public static String escapeRegex(String str) {
        if (str == null) {
            return null;
        }
        return Pattern.quote(str);
    }

    /**
     * Check if a string contains only ASCII characters.
     *
     * @param str String to check
     * @return true if ASCII only
     */
    public static boolean isAscii(String str) {
        if (str == null) {
            return true;
        }
        return str.chars().allMatch(c -> c < 128);
    }

    /**
     * Normalize line endings to Unix style (\n).
     *
     * @param str String to normalize
     * @return String with normalized line endings
     */
    public static String normalizeLineEndings(String str) {
        if (str == null) {
            return null;
        }
        return str.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");
    }

    /**
     * Extract the first N words from a string.
     *
     * @param str String to extract from
     * @param wordCount Number of words to extract
     * @return First N words
     */
    public static String firstWords(String str, int wordCount) {
        if (str == null || wordCount <= 0) {
            return "";
        }
        String[] words = str.split("\\s+");
        int count = Math.min(wordCount, words.length);
        return String.join(" ", java.util.Arrays.copyOfRange(words, 0, count));
    }
}



package com.ibm.codeanalyzer.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;

/**
 * Validation utility methods for input validation.
 */
public class ValidationUtils {

    private static final Pattern GIT_URL_PATTERN = Pattern.compile(
        "^(https?://|git@)[\\w.-]+[:/][\\w.-]+/[\\w.-]+(\\.git)?$"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    private static final Pattern SAFE_FILENAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    private ValidationUtils() {
        // Utility class
    }

    /**
     * Validate if a string is a valid Git repository URL.
     *
     * @param url URL to validate
     * @return true if valid Git URL
     */
    public static boolean isValidGitUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        return GIT_URL_PATTERN.matcher(url.trim()).matches();
    }

    /**
     * Validate if a string is a valid HTTP/HTTPS URL.
     *
     * @param url URL to validate
     * @return true if valid URL
     */
    public static boolean isValidUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return false;
        }
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    /**
     * Validate if a string is a valid email address.
     *
     * @param email Email to validate
     * @return true if valid email
     */
    public static boolean isValidEmail(String email) {
        if (StringUtils.isBlank(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Validate if a string contains only alphanumeric characters.
     *
     * @param str String to validate
     * @return true if alphanumeric
     */
    public static boolean isAlphanumeric(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        return ALPHANUMERIC_PATTERN.matcher(str).matches();
    }

    /**
     * Validate if a string is a safe filename (no path traversal).
     *
     * @param filename Filename to validate
     * @return true if safe filename
     */
    public static boolean isSafeFilename(String filename) {
        if (StringUtils.isBlank(filename)) {
            return false;
        }
        // Check for path traversal attempts
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return false;
        }
        return SAFE_FILENAME_PATTERN.matcher(filename).matches();
    }

    /**
     * Validate if a string is within a length range.
     *
     * @param str String to validate
     * @param minLength Minimum length (inclusive)
     * @param maxLength Maximum length (inclusive)
     * @return true if within range
     */
    public static boolean isLengthInRange(String str, int minLength, int maxLength) {
        if (str == null) {
            return false;
        }
        int length = str.length();
        return length >= minLength && length <= maxLength;
    }

    /**
     * Validate if a number is within a range.
     *
     * @param value Value to validate
     * @param min Minimum value (inclusive)
     * @param max Maximum value (inclusive)
     * @return true if within range
     */
    public static boolean isInRange(int value, int min, int max) {
        return value >= min && value <= max;
    }

    /**
     * Validate if a number is positive.
     *
     * @param value Value to validate
     * @return true if positive
     */
    public static boolean isPositive(int value) {
        return value > 0;
    }

    /**
     * Validate if a number is non-negative.
     *
     * @param value Value to validate
     * @return true if non-negative
     */
    public static boolean isNonNegative(int value) {
        return value >= 0;
    }

    /**
     * Validate if a string matches a regex pattern.
     *
     * @param str String to validate
     * @param pattern Regex pattern
     * @return true if matches
     */
    public static boolean matchesPattern(String str, String pattern) {
        if (str == null || pattern == null) {
            return false;
        }
        return Pattern.compile(pattern).matcher(str).matches();
    }

    /**
     * Validate if a string is a valid Java package name.
     *
     * @param packageName Package name to validate
     * @return true if valid package name
     */
    public static boolean isValidJavaPackageName(String packageName) {
        if (StringUtils.isBlank(packageName)) {
            return false;
        }
        // Package name should be lowercase and dot-separated
        Pattern pattern = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$");
        return pattern.matcher(packageName).matches();
    }

    /**
     * Validate if a string is a valid Java class name.
     *
     * @param className Class name to validate
     * @return true if valid class name
     */
    public static boolean isValidJavaClassName(String className) {
        if (StringUtils.isBlank(className)) {
            return false;
        }
        // Class name should start with uppercase and be PascalCase
        Pattern pattern = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");
        return pattern.matcher(className).matches();
    }

    /**
     * Validate if a string contains SQL injection patterns.
     *
     * @param input Input to validate
     * @return true if potentially contains SQL injection
     */
    public static boolean containsSqlInjectionPattern(String input) {
        if (StringUtils.isBlank(input)) {
            return false;
        }
        String lower = input.toLowerCase();
        String[] sqlKeywords = {
            "select", "insert", "update", "delete", "drop", "create",
            "alter", "exec", "execute", "union", "declare", "--", "/*", "*/"
        };
        for (String keyword : sqlKeywords) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validate if a string contains XSS patterns.
     *
     * @param input Input to validate
     * @return true if potentially contains XSS
     */
    public static boolean containsXssPattern(String input) {
        if (StringUtils.isBlank(input)) {
            return false;
        }
        String lower = input.toLowerCase();
        String[] xssPatterns = {
            "<script", "javascript:", "onerror=", "onload=", "onclick=",
            "<iframe", "<object", "<embed", "eval("
        };
        for (String pattern : xssPatterns) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sanitize a string by removing potentially dangerous characters.
     *
     * @param input Input to sanitize
     * @return Sanitized string
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        // Remove control characters and non-printable characters
        return input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "")
                   .replaceAll("[^\\p{Print}]", "");
    }

    /**
     * Validate if a port number is valid.
     *
     * @param port Port number
     * @return true if valid port (1-65535)
     */
    public static boolean isValidPort(int port) {
        return port >= 1 && port <= 65535;
    }

    /**
     * Validate if a string is a valid IPv4 address.
     *
     * @param ip IP address to validate
     * @return true if valid IPv4
     */
    public static boolean isValidIpv4(String ip) {
        if (StringUtils.isBlank(ip)) {
            return false;
        }
        Pattern pattern = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
        );
        return pattern.matcher(ip).matches();
    }
}



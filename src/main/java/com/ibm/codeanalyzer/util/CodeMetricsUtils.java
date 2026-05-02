package com.ibm.codeanalyzer.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for calculating code metrics.
 */
public class CodeMetricsUtils {

    private static final Pattern COMMENT_LINE_PATTERN = Pattern.compile("^\\s*(//|/\\*|\\*|\\*/)");
    private static final Pattern BLANK_LINE_PATTERN = Pattern.compile("^\\s*$");

    private CodeMetricsUtils() {
        // Utility class
    }

    /**
     * Count lines of code (LOC) excluding comments and blank lines.
     *
     * @param content Source code content
     * @return Number of lines of code
     */
    public static int countLinesOfCode(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        String[] lines = content.split("\n");
        int loc = 0;
        boolean inBlockComment = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // Check for block comment start/end
            if (trimmed.contains("/*")) {
                inBlockComment = true;
            }
            if (trimmed.contains("*/")) {
                inBlockComment = false;
                continue;
            }

            // Skip if in block comment, blank line, or single-line comment
            if (inBlockComment || trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }

            loc++;
        }

        return loc;
    }

    /**
     * Count comment lines in source code.
     *
     * @param content Source code content
     * @return Number of comment lines
     */
    public static int countCommentLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        String[] lines = content.split("\n");
        int comments = 0;
        boolean inBlockComment = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.contains("/*")) {
                inBlockComment = true;
                comments++;
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }

            if (inBlockComment) {
                comments++;
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }

            if (trimmed.startsWith("//")) {
                comments++;
            }
        }

        return comments;
    }

    /**
     * Count blank lines in source code.
     *
     * @param content Source code content
     * @return Number of blank lines
     */
    public static int countBlankLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        String[] lines = content.split("\n");
        int blank = 0;

        for (String line : lines) {
            if (BLANK_LINE_PATTERN.matcher(line).matches()) {
                blank++;
            }
        }

        return blank;
    }

    /**
     * Calculate comment density (comment lines / total lines).
     *
     * @param content Source code content
     * @return Comment density as a percentage (0-100)
     */
    public static double calculateCommentDensity(String content) {
        if (content == null || content.isEmpty()) {
            return 0.0;
        }

        int totalLines = content.split("\n").length;
        int commentLines = countCommentLines(content);

        if (totalLines == 0) {
            return 0.0;
        }

        return (commentLines * 100.0) / totalLines;
    }

    /**
     * Count the number of methods in Java source code (simple heuristic).
     *
     * @param content Java source code
     * @return Approximate number of methods
     */
    public static int countMethods(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        // Simple pattern to match method declarations
        // Matches: public/private/protected [static] [final] Type methodName(
        Pattern methodPattern = Pattern.compile(
            "(public|private|protected)\\s+(static\\s+)?(final\\s+)?\\w+(<[^>]+>)?\\s+\\w+\\s*\\(",
            Pattern.MULTILINE
        );

        Matcher matcher = methodPattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }

        return count;
    }

    /**
     * Count the number of classes in Java source code.
     *
     * @param content Java source code
     * @return Number of classes
     */
    public static int countClasses(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        Pattern classPattern = Pattern.compile(
            "(public|private|protected)?\\s*(static\\s+)?(final\\s+)?(abstract\\s+)?class\\s+\\w+",
            Pattern.MULTILINE
        );

        Matcher matcher = classPattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }

        return count;
    }

    /**
     * Count the number of imports in Java source code.
     *
     * @param content Java source code
     * @return Number of imports
     */
    public static int countImports(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        Pattern importPattern = Pattern.compile("^import\\s+", Pattern.MULTILINE);
        Matcher matcher = importPattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }

        return count;
    }

    /**
     * Calculate average method length in lines.
     *
     * @param content Source code content
     * @return Average method length
     */
    public static double calculateAverageMethodLength(String content) {
        if (content == null || content.isEmpty()) {
            return 0.0;
        }

        int methodCount = countMethods(content);
        if (methodCount == 0) {
            return 0.0;
        }

        int loc = countLinesOfCode(content);
        return (double) loc / methodCount;
    }

    /**
     * Estimate code complexity based on control flow keywords.
     *
     * @param content Source code content
     * @return Complexity score
     */
    public static int estimateComplexity(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }

        int complexity = 1; // Base complexity

        // Count control flow keywords
        String[] keywords = {"if", "else", "for", "while", "do", "switch", "case", "catch", "&&", "||", "?"};

        for (String keyword : keywords) {
            Pattern pattern = Pattern.compile("\\b" + keyword + "\\b");
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                complexity++;
            }
        }

        return complexity;
    }

    /**
     * Calculate maintainability index (simplified version).
     * MI = 171 - 5.2 * ln(HV) - 0.23 * CC - 16.2 * ln(LOC)
     * Where HV = Halstead Volume, CC = Cyclomatic Complexity, LOC = Lines of Code
     * 
     * This is a simplified version using only LOC and estimated complexity.
     *
     * @param content Source code content
     * @return Maintainability index (0-100, higher is better)
     */
    public static double calculateMaintainabilityIndex(String content) {
        if (content == null || content.isEmpty()) {
            return 100.0;
        }

        int loc = countLinesOfCode(content);
        int complexity = estimateComplexity(content);

        if (loc == 0) {
            return 100.0;
        }

        // Simplified formula
        double mi = 171 - 5.2 * Math.log(loc) - 0.23 * complexity - 16.2 * Math.log(loc);
        
        // Normalize to 0-100 range
        mi = Math.max(0, Math.min(100, mi));

        return mi;
    }

    /**
     * Check if code has adequate comments (at least 10% comment density).
     *
     * @param content Source code content
     * @return true if adequately commented
     */
    public static boolean hasAdequateComments(String content) {
        return calculateCommentDensity(content) >= 10.0;
    }
}



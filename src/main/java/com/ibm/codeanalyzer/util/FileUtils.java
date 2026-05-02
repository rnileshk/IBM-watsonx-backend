package com.ibm.codeanalyzer.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * File utility methods for working with source code files and directories.
 */
@Slf4j
public class FileUtils {

    private FileUtils() {
        // Utility class
    }

    /**
     * Recursively delete a directory and all its contents.
     *
     * @param path Directory to delete
     * @throws IOException if deletion fails
     */
    public static void deleteDirectory(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Get file extension from a file path.
     *
     * @param filePath File path
     * @return Extension (e.g., ".java") or empty string if no extension
     */
    public static String getFileExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot);
        }
        return "";
    }

    /**
     * Get file name without extension.
     *
     * @param filePath File path
     * @return File name without extension
     */
    public static String getFileNameWithoutExtension(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }
        String fileName = Paths.get(filePath).getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }

    /**
     * Check if a file is a text file based on extension.
     *
     * @param filePath File path
     * @return true if text file
     */
    public static boolean isTextFile(String filePath) {
        String ext = getFileExtension(filePath).toLowerCase();
        return ext.matches("\\.(java|py|js|ts|go|cs|cpp|c|rb|php|kt|scala|xml|yaml|yml|properties|gradle|json|txt|md)");
    }

    /**
     * Count lines in a file.
     *
     * @param content File content
     * @return Number of lines
     */
    public static int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.split("\n").length;
    }

    /**
     * Find all files matching a pattern in a directory.
     *
     * @param directory Directory to search
     * @param pattern File pattern (e.g., "*.java")
     * @return List of matching file paths
     * @throws IOException if search fails
     */
    public static List<Path> findFiles(Path directory, String pattern) throws IOException {
        List<Path> result = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (matcher.matches(file.getFileName())) {
                    result.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return result;
    }

    /**
     * Sanitize a file name by removing invalid characters.
     *
     * @param fileName File name to sanitize
     * @return Sanitized file name
     */
    public static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unnamed";
        }
        // Remove invalid characters for file names
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Calculate directory size in bytes.
     *
     * @param directory Directory path
     * @return Size in bytes
     * @throws IOException if calculation fails
     */
    public static long getDirectorySize(Path directory) throws IOException {
        final long[] size = {0};
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size[0] += attrs.size();
                return FileVisitResult.CONTINUE;
            }
        });
        return size[0];
    }
}



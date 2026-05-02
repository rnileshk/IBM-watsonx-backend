package com.ibm.codeanalyzer.service;

import com.ibm.codeanalyzer.model.AnalysisRequest;
import com.ibm.codeanalyzer.model.CodeChunk;
import com.ibm.codeanalyzer.model.exception.IngestionException;
import com.ibm.codeanalyzer.model.exception.InvalidInputException;
import com.ibm.codeanalyzer.util.ValidationUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles all ingestion paths:
 *   1. Git repository clone (via JGit)
 *   2. ZIP / file upload extraction
 *   3. Raw code paste
 *
 * After loading source files, splits them into LLM-ready chunks.
 */
@Slf4j
@Service
public class IngestionService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".py", ".js", ".ts", ".go", ".cs", ".cpp", ".c",
            ".rb", ".php", ".kt", ".scala", ".xml", ".yaml", ".yml",
            ".properties", ".gradle", ".json"
    );

    private static final Set<String> SKIP_DIRS = Set.of(
            ".git", "node_modules", "target", "build", "__pycache__",
            ".idea", ".mvn", "dist", "out", ".gradle"
    );

    @Value("${analyzer.work-dir:/tmp/code-analyzer}")
    private String workDir;

    @Value("${llm.chunk-size-chars:8000}")
    private int chunkSizeChars;

    // ─────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * Resolves source files from the request and returns them as a flat map
     * of { relativePath → fileContent }.
     */
    public Map<String, String> ingestSources(AnalysisRequest request, String jobId) {
        Path jobDir;
        try {
            jobDir = prepareJobDir(jobId);
        } catch (IOException e) {
            throw new IngestionException("Failed to create job directory: " + e.getMessage(), e);
        }

        return switch (request.getInputType()) {
            case GIT_URL      -> ingestFromGit(request.getGitUrl(), request.getBranch(), jobDir);
            case RAW_CODE     -> ingestRawCode(request.getRawCode(), request.getFileName());
            case FILE_UPLOAD  -> throw new IllegalStateException(
                    "FILE_UPLOAD must call ingestFromUpload() with the MultipartFile");
        };
    }

    /**
     * Handles a multipart ZIP or single source file upload.
     */
    public Map<String, String> ingestFromUpload(MultipartFile file, String jobId) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("Uploaded file is empty");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !ValidationUtils.isSafeFilename(originalName)) {
            throw new InvalidInputException("Invalid or unsafe filename");
        }
        Path jobDir;
        try {
            jobDir = prepareJobDir(jobId);
        } catch (IOException e) {
            throw new IngestionException("Failed to create job directory: " + e.getMessage(), e);
        }

        if (originalName.endsWith(".zip")) {
            try {
                Path zipPath = jobDir.resolve("upload.zip");
                file.transferTo(zipPath.toFile());
                return extractZip(zipPath, jobDir);
            } catch (IOException e) {
                throw new IngestionException("Failed to process ZIP file: " + e.getMessage(), e);
            }
        } else {
            // Single file upload
            try {
                String content = new String(file.getBytes());
                return Map.of(originalName, content);
            } catch (IOException e) {
                throw new IngestionException("Failed to read uploaded file: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Splits source files into chunks sized for the LLM context window.
     */
    public List<CodeChunk> chunkSources(Map<String, String> sources) {
        List<CodeChunk> chunks = new ArrayList<>();

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String filePath = entry.getKey();
            String content  = entry.getValue();
            String language = detectLanguage(filePath);

            if (content.length() <= chunkSizeChars) {
                chunks.add(CodeChunk.builder()
                        .filePath(filePath)
                        .chunkIndex(0)
                        .totalChunks(1)
                        .content(content)
                        .language(language)
                        .startLine(1)
                        .endLine(content.split("\n").length)
                        .build());
            } else {
                // Overlap by 10 % to preserve context across chunk boundaries
                int overlap = (int) (chunkSizeChars * 0.1);
                int step = chunkSizeChars - overlap;
                int totalChunks = (int) Math.ceil((double) content.length() / step);
                String[] lines = content.split("\n");

                for (int i = 0; i < totalChunks; i++) {
                    int start = i * step;
                    int end   = Math.min(start + chunkSizeChars, content.length());
                    String chunkContent = content.substring(start, end);

                    // Approximate line numbers
                    int startLine = content.substring(0, start).split("\n").length;
                    int endLine   = content.substring(0, end).split("\n").length;

                    chunks.add(CodeChunk.builder()
                            .filePath(filePath)
                            .chunkIndex(i)
                            .totalChunks(totalChunks)
                            .content(chunkContent)
                            .language(language)
                            .startLine(startLine)
                            .endLine(endLine)
                            .build());
                }
            }
        }

        return chunks;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────

    private Map<String, String> ingestFromGit(String gitUrl, String branch, Path jobDir) {
        if (!ValidationUtils.isValidGitUrl(gitUrl)) {
            throw new InvalidInputException("Invalid Git repository URL: " + gitUrl);
        }

        log.info("Cloning repository: {}", gitUrl);
        Path repoDir = jobDir.resolve("repo");

        var cloneCmd = Git.cloneRepository()
                .setURI(gitUrl)
                .setDirectory(repoDir.toFile())
                .setDepth(1)          // shallow clone — only latest snapshot
                .setTimeout(300);     // 5 minute timeout

        if (branch != null && !branch.isBlank()) {
            cloneCmd.setBranch(branch);
        }

        try (Git git = cloneCmd.call()) {
            log.info("Cloned {} to {}", gitUrl, repoDir);
        } catch (GitAPIException e) {
            throw new IngestionException("Failed to clone Git repository: " + e.getMessage(), e);
        }

        try {
            return walkDirectory(repoDir);
        } catch (IOException e) {
            throw new IngestionException("Failed to read repository files: " + e.getMessage(), e);
        }
    }

    private Map<String, String> ingestRawCode(String rawCode, String fileName) {
        String name = (fileName != null && !fileName.isBlank()) ? fileName : "snippet.java";
        return Map.of(name, rawCode);
    }

    private Map<String, String> extractZip(Path zipPath, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    Path outPath = destDir.resolve(entry.getName()).normalize();
                    // Security: prevent zip-slip
                    if (!outPath.startsWith(destDir)) {
                        log.warn("Skipping zip-slip entry: {}", entry.getName());
                        continue;
                    }
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
        return walkDirectory(destDir);
    }

    private Map<String, String> walkDirectory(Path root) throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();

        Files.walk(root)
                .filter(Files::isRegularFile)
                .filter(p -> !isInSkippedDir(p))
                .filter(p -> isSupportedExtension(p.toString()))
                .forEach(p -> {
                    try {
                        String relativePath = root.relativize(p).toString();
                        String content = Files.readString(p);
                        sources.put(relativePath, content);
                        log.debug("Ingested: {}", relativePath);
                    } catch (IOException e) {
                        log.warn("Could not read file {}: {}", p, e.getMessage());
                    }
                });

        log.info("Ingested {} source files", sources.size());
        return sources;
    }

    private Path prepareJobDir(String jobId) throws IOException {
        Path dir = Path.of(workDir, jobId);
        Files.createDirectories(dir);
        return dir;
    }

    private boolean isInSkippedDir(Path path) {
        for (Path part : path) {
            if (SKIP_DIRS.contains(part.toString())) return true;
        }
        return false;
    }

    private boolean isSupportedExtension(String fileName) {
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    public String detectLanguage(String fileName) {
        if (fileName.endsWith(".java"))  return "java";
        if (fileName.endsWith(".py"))    return "python";
        if (fileName.endsWith(".js"))    return "javascript";
        if (fileName.endsWith(".ts"))    return "typescript";
        if (fileName.endsWith(".go"))    return "go";
        if (fileName.endsWith(".cs"))    return "csharp";
        if (fileName.endsWith(".kt"))    return "kotlin";
        if (fileName.endsWith(".rb"))    return "ruby";
        if (fileName.endsWith(".php"))   return "php";
        if (fileName.endsWith(".scala")) return "scala";
        return "unknown";
    }

    /** Clean up the temp directory for a finished job */
    public void cleanup(String jobId) {
        try {
            Path dir = Path.of(workDir, jobId);
            if (Files.exists(dir)) {
                FileUtils.deleteDirectory(dir.toFile());
                log.info("Cleaned up job directory: {}", jobId);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up job {}: {}", jobId, e.getMessage());
        }
    }
}

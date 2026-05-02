package com.ibm.codeanalyzer.config;

import java.util.Set;

/**
 * Application-wide constants.
 */
public final class Constants {

    private Constants() {
        // Utility class
    }

    // ── API Endpoints ────────────────────────────────────────────────────
    public static final String API_BASE_PATH = "/api/v1";
    public static final String ANALYZE_URL_ENDPOINT = API_BASE_PATH + "/analyze/url";
    public static final String ANALYZE_CODE_ENDPOINT = API_BASE_PATH + "/analyze/code";
    public static final String ANALYZE_UPLOAD_ENDPOINT = API_BASE_PATH + "/analyze/upload";
    public static final String ANALYZE_STATUS_ENDPOINT = API_BASE_PATH + "/analyze/{jobId}";
    public static final String ANALYZE_JOBS_ENDPOINT = API_BASE_PATH + "/analyze/jobs";
    public static final String HEALTH_ENDPOINT = API_BASE_PATH + "/health";

    // ── File Extensions ──────────────────────────────────────────────────
    public static final Set<String> SUPPORTED_SOURCE_EXTENSIONS = Set.of(
        ".java", ".py", ".js", ".ts", ".go", ".cs", ".cpp", ".c",
        ".rb", ".php", ".kt", ".scala", ".rs", ".swift", ".m", ".h"
    );

    public static final Set<String> SUPPORTED_CONFIG_EXTENSIONS = Set.of(
        ".xml", ".yaml", ".yml", ".properties", ".gradle", ".json",
        ".toml", ".ini", ".conf"
    );

    public static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
        ".zip", ".tar", ".gz", ".tar.gz", ".tgz"
    );

    // ── Directory Exclusions ─────────────────────────────────────────────
    public static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
        ".git", ".svn", ".hg",
        "node_modules", "bower_components",
        "target", "build", "dist", "out",
        "__pycache__", ".pytest_cache",
        ".idea", ".vscode", ".eclipse",
        ".mvn", ".gradle",
        "vendor", "packages"
    );

    // ── Analysis Thresholds ──────────────────────────────────────────────
    public static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
    public static final int MAX_UPLOAD_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB
    public static final int MAX_METHOD_LENGTH_LINES = 50;
    public static final int MAX_CLASS_LENGTH_LINES = 500;
    public static final int MAX_CYCLOMATIC_COMPLEXITY = 10;
    public static final int MAX_METHOD_PARAMETERS = 5;
    public static final int MAX_NESTING_DEPTH = 4;
    public static final int MIN_COMMENT_DENSITY_PERCENT = 10;

    // ── LLM Configuration ────────────────────────────────────────────────
    public static final int DEFAULT_LLM_MAX_TOKENS = 2000;
    public static final int DEFAULT_LLM_CHUNK_SIZE_CHARS = 8000;
    public static final int DEFAULT_LLM_TIMEOUT_SECONDS = 60;
    public static final double DEFAULT_LLM_TEMPERATURE = 0.2;
    public static final double DEFAULT_LLM_TOP_P = 0.9;

    // ── Provider Names ───────────────────────────────────────────────────
    public static final String PROVIDER_WATSONX = "watsonx";
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_ANTHROPIC = "anthropic";

    // ── Analysis Sources ─────────────────────────────────────────────────
    public static final String SOURCE_OWASP_SCANNER = "OWASP_SCANNER";
    public static final String SOURCE_QUALITY_SCANNER = "QUALITY_SCANNER";
    public static final String SOURCE_JAVAPARSER_AST = "JAVAPARSER_AST";
    public static final String SOURCE_STRUCTURAL_METRICS = "STRUCTURAL_METRICS";
    public static final String SOURCE_LLM_PREFIX = "LLM_";
    public static final String SOURCE_SEMGREP = "SEMGREP";

    // ── Rule ID Prefixes ─────────────────────────────────────────────────
    public static final String RULE_OWASP_PREFIX = "OWASP-";
    public static final String RULE_QUALITY_PREFIX = "QA-";
    public static final String RULE_LLM_PREFIX = "LLM-ARCH-";

    // ── HTTP Headers ─────────────────────────────────────────────────────
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_API_KEY = "x-api-key";
    public static final String HEADER_ANTHROPIC_VERSION = "anthropic-version";

    // ── Status Messages ──────────────────────────────────────────────────
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    // ── Error Messages ───────────────────────────────────────────────────
    public static final String ERROR_INVALID_GIT_URL = "Invalid Git repository URL";
    public static final String ERROR_INVALID_FILE_TYPE = "Unsupported file type";
    public static final String ERROR_FILE_TOO_LARGE = "File size exceeds maximum allowed size";
    public static final String ERROR_EMPTY_CODE = "Code content cannot be empty";
    public static final String ERROR_JOB_NOT_FOUND = "Analysis job not found";
    public static final String ERROR_ANALYSIS_FAILED = "Analysis failed";
    public static final String ERROR_LLM_TIMEOUT = "LLM request timed out";
    public static final String ERROR_LLM_API_ERROR = "LLM API error";

    // ── Default Values ───────────────────────────────────────────────────
    public static final String DEFAULT_PROJECT_NAME = "Unnamed Project";
    public static final String DEFAULT_FILE_NAME = "snippet.java";
    public static final String DEFAULT_BRANCH = "main";
    public static final String DEFAULT_LANGUAGE = "unknown";

    // ── Regex Patterns ───────────────────────────────────────────────────
    public static final String REGEX_GIT_URL = "^(https?://|git@)[\\w.-]+[:/][\\w.-]+/[\\w.-]+(\\.git)?$";
    public static final String REGEX_EMAIL = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public static final String REGEX_ALPHANUMERIC = "^[a-zA-Z0-9]+$";
    public static final String REGEX_SAFE_FILENAME = "^[a-zA-Z0-9._-]+$";

    // ── Time Constants ───────────────────────────────────────────────────
    public static final int ASYNC_CORE_POOL_SIZE = 4;
    public static final int ASYNC_MAX_POOL_SIZE = 10;
    public static final int ASYNC_QUEUE_CAPACITY = 25;
    public static final int SHUTDOWN_TIMEOUT_SECONDS = 60;

    // ── Severity Levels ──────────────────────────────────────────────────
    public static final String SEVERITY_CRITICAL = "CRITICAL";
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_LOW = "LOW";
    public static final String SEVERITY_INFO = "INFO";

    // ── Category Names ───────────────────────────────────────────────────
    public static final String CATEGORY_SECURITY = "SECURITY";
    public static final String CATEGORY_CODE_QUALITY = "CODE_QUALITY";
    public static final String CATEGORY_ARCHITECTURE = "ARCHITECTURE";
    public static final String CATEGORY_PERFORMANCE = "PERFORMANCE";
    public static final String CATEGORY_DEPENDENCY = "DEPENDENCY";

    // ── Language Names ───────────────────────────────────────────────────
    public static final String LANG_JAVA = "java";
    public static final String LANG_PYTHON = "python";
    public static final String LANG_JAVASCRIPT = "javascript";
    public static final String LANG_TYPESCRIPT = "typescript";
    public static final String LANG_GO = "go";
    public static final String LANG_CSHARP = "csharp";
    public static final String LANG_CPP = "cpp";
    public static final String LANG_KOTLIN = "kotlin";
    public static final String LANG_SCALA = "scala";
    public static final String LANG_RUBY = "ruby";
    public static final String LANG_PHP = "php";

    // ── OWASP Top 10 Categories ──────────────────────────────────────────
    public static final String OWASP_A01_BROKEN_ACCESS_CONTROL = "A01:2021-Broken Access Control";
    public static final String OWASP_A02_CRYPTOGRAPHIC_FAILURES = "A02:2021-Cryptographic Failures";
    public static final String OWASP_A03_INJECTION = "A03:2021-Injection";
    public static final String OWASP_A04_INSECURE_DESIGN = "A04:2021-Insecure Design";
    public static final String OWASP_A05_SECURITY_MISCONFIGURATION = "A05:2021-Security Misconfiguration";
    public static final String OWASP_A06_VULNERABLE_COMPONENTS = "A06:2021-Vulnerable and Outdated Components";
    public static final String OWASP_A07_AUTH_FAILURES = "A07:2021-Identification and Authentication Failures";
    public static final String OWASP_A08_DATA_INTEGRITY = "A08:2021-Software and Data Integrity Failures";
    public static final String OWASP_A09_LOGGING_FAILURES = "A09:2021-Security Logging and Monitoring Failures";
    public static final String OWASP_A10_SSRF = "A10:2021-Server-Side Request Forgery";

    // ── Chunk Overlap ────────────────────────────────────────────────────
    public static final double CHUNK_OVERLAP_PERCENTAGE = 0.1; // 10% overlap

    // ── Version ──────────────────────────────────────────────────────────
    public static final String APPLICATION_VERSION = "1.0.0";
    public static final String APPLICATION_NAME = "IBM Code Analyzer";
}



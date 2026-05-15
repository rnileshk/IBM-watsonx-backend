package com.ibm.codeanalyzer.service;

import com.ibm.codeanalyzer.model.Finding;
import com.ibm.codeanalyzer.model.Finding.Category;
import com.ibm.codeanalyzer.model.Finding.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
public class SecurityScanService {

    @Value("${security.semgrep.enabled:false}")
    private boolean semgrepEnabled;

    @Value("${security.semgrep.binary:semgrep}")
    private String semgrepBinary;

    private record SecurityRule(
            String ruleId,
            String title,
            String description,
            String recommendation,
            Severity severity,
            Pattern pattern
    ) {}

    private static final List<SecurityRule> OWASP_RULES = List.of(

            new SecurityRule(
                    "OWASP-A03-SQL-001",
                    "Potential SQL Injection",
                    "String concatenation used to build a SQL query. User input may reach this directly.",
                    "Use PreparedStatement with parameterized queries. Never concatenate user input into SQL strings.",
                    Severity.CRITICAL,
                    Pattern.compile(
                            "(Statement|createStatement)\\s*\\.[a-zA-Z]*execute(Query|Update)?\\s*\\([^;]*\\+[^;]*\\)|" +
                            "\"(SELECT|INSERT|UPDATE|DELETE)[^\"]*\"\\s*\\+\\s*[a-zA-Z0-9_]+",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A03-CMD-001",
                    "Potential Command Injection",
                    "Runtime.exec() or ProcessBuilder invoked with a value that may include user input.",
                    "Validate and sanitize all input before passing to system commands. Use an allowlist of permitted commands.",
                    Severity.CRITICAL,
                    Pattern.compile(
                            "Runtime\\.getRuntime\\s*\\(\\s*\\)\\.exec\\s*\\([^;]*\\+[^;]*\\)|" +
                            "new\\s+ProcessBuilder\\s*\\([^;]*\\+[^;]*\\)",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A02-CRED-001",
                    "Hardcoded Password or Secret",
                    "A hardcoded password, secret, or API key was found. This leaks credentials in source control.",
                    "Use environment variables, a secrets manager, or Spring Boot externalized configuration.",
                    Severity.HIGH,
                    Pattern.compile(
                            "(password|passwd|secret|access[_-]?token)\\s*=\\s*\"(?!your-|change-me|example|dummy|test)[^\"]{8,}\"|" +
                            "(api[_-]?key)\\s*=\\s*\"(?!x-api-key|api-key|authorization|content-type)[^\"]{12,}\"",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A02-CRYPTO-001",
                    "Weak Cryptographic Algorithm",
                    "MD5 or SHA-1 are cryptographically broken and must not be used for passwords or security-sensitive hashing.",
                    "Use SHA-256 or stronger for integrity checks. For passwords, use BCrypt, Argon2, or PBKDF2.",
                    Severity.HIGH,
                    Pattern.compile(
                            "MessageDigest\\.getInstance\\s*\\(\\s*\"(MD5|SHA-1|SHA1)\"\\s*\\)",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A02-RAND-001",
                    "Insecure Random Number Generator",
                    "java.util.Random is not cryptographically secure and must not be used for tokens, session IDs, or security-sensitive values.",
                    "Replace java.util.Random with java.security.SecureRandom for any security-sensitive context.",
                    Severity.MEDIUM,
                    Pattern.compile(
                            "new\\s+Random\\s*\\(\\s*\\)|Math\\.random\\s*\\(\\s*\\)",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A08-DESER-001",
                    "Unsafe Java Deserialization",
                    "Unsafe Java native deserialization can execute arbitrary code if attacker-controlled data is processed.",
                    "Avoid Java native deserialization for untrusted data. Use JSON or Protobuf. If deserialization is necessary, use ObjectInputFilter or a strict allowlist.",
                    Severity.CRITICAL,
                    Pattern.compile(
                            "new\\s+ObjectInputStream\\s*\\([^)]*\\)|" +
                            "[a-zA-Z_$][a-zA-Z0-9_$]*\\s*\\.\\s*readObject\\s*\\(",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A01-AUTH-001",
                    "Missing Authorization Annotation",
                    "A public mapping endpoint may be missing @PreAuthorize, @Secured, or @RolesAllowed.",
                    "Add authorization annotations or configure HttpSecurity rules for protected endpoints. Exclude public endpoints intentionally.",
                    Severity.HIGH,
                    Pattern.compile(
                            "@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)" +
                            "(?![\\s\\S]{0,250}(health|actuator|login|register|public|status))" +
                            "(?![\\s\\S]{0,250}@(PreAuthorize|Secured|RolesAllowed))",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A05-CSRF-001",
                    "CSRF Protection Disabled",
                    "csrf().disable() turns off Spring Security's Cross-Site Request Forgery protection.",
                    "Enable CSRF for cookie-based sessions. For stateless JWT APIs, document why CSRF is disabled.",
                    Severity.HIGH,
                    Pattern.compile(
                            "\\.csrf\\s*\\(\\s*\\)\\s*\\.disable\\s*\\(\\s*\\)|csrf\\s*\\.\\s*disable",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A05-TLS-001",
                    "SSL/TLS Certificate Validation Disabled",
                    "SSL/TLS certificate validation appears to be disabled.",
                    "Use a properly configured TrustManager with a valid certificate chain. Never disable certificate validation in production.",
                    Severity.CRITICAL,
                    Pattern.compile(
                            "setHostnameVerifier\\s*\\(\\s*(NoopHostnameVerifier\\.INSTANCE|[^)]*ALLOW_ALL_HOSTNAME_VERIFIER[^)]*)\\s*\\)|" +
                            "new\\s+X509TrustManager\\s*\\(\\s*\\)\\s*\\{[\\s\\S]{0,700}" +
                            "checkServerTrusted\\s*\\([^)]*\\)\\s*\\{\\s*\\}",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A07-PWD-001",
                    "Plaintext Password Comparison",
                    "Passwords compared directly with equals() may indicate plaintext password handling.",
                    "Hash passwords with BCrypt, Argon2, or PBKDF2 and use proper password verification.",
                    Severity.CRITICAL,
                    Pattern.compile(
                            "password\\.equals\\s*\\(|equals\\s*\\(\\s*password|getPassword\\s*\\(\\s*\\)\\.equals",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A09-LOG-001",
                    "Sensitive Data Logged",
                    "Password, token, or secret-related variables are passed to a logger.",
                    "Never log passwords, tokens, or secrets. Redact sensitive fields before logging.",
                    Severity.MEDIUM,
                    Pattern.compile(
                            "log\\.(info|debug|warn|error)\\s*\\([^;]*(password|token|secret)",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A03-LDAP-001",
                    "Potential LDAP Injection",
                    "LDAP filter constructed with string concatenation. Attacker-controlled input can alter the LDAP query.",
                    "Use parameterized LDAP filters or sanitize input using OWASP LDAP encoding.",
                    Severity.HIGH,
                    Pattern.compile(
                            "(DirContext|LdapTemplate)\\s+[a-zA-Z_$][a-zA-Z0-9_$]*[\\s\\S]{0,300}" +
                            "\\.search\\s*\\([^;]*\\+[^;]*\\)",
                            Pattern.CASE_INSENSITIVE
                    )
            ),

            new SecurityRule(
                    "OWASP-A03-XXE-001",
                    "XML External Entity Risk",
                    "XML parser factory created without obvious external entity hardening.",
                    "Disable DOCTYPE declarations and external entity resolution before parsing XML.",
                    Severity.HIGH,
                    Pattern.compile(
                            "DocumentBuilderFactory\\.newInstance\\s*\\(\\s*\\)|" +
                            "SAXParserFactory\\.newInstance\\s*\\(\\s*\\)|" +
                            "XMLInputFactory\\.newInstance\\s*\\(\\s*\\)",
                            Pattern.CASE_INSENSITIVE
                    )
            )
    );

    public List<Finding> scan(Map<String, String> sources) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }

        log.info("Starting security scan on {} files", sources.size());

        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String filePath = normalizePath(entry.getKey());
            String content = entry.getValue();

            if (shouldSkipFile(filePath, content)) {
                continue;
            }

            findings.addAll(applyOwaspRules(filePath, content));
        }

        if (semgrepEnabled) {
            log.info("Semgrep integration enabled but requires filesystem paths; skipping in-memory scan.");
        }

        List<Finding> deduped = deduplicate(findings);

        log.info("Security scan complete - {} findings, {} after deduplication",
                findings.size(), deduped.size());

        return deduped;
    }

    private boolean shouldSkipFile(String filePath, String content) {
        if (filePath == null || filePath.isBlank()) {
            return true;
        }

        if (content == null || content.isBlank()) {
            return true;
        }

        String normalized = normalizePath(filePath);

        return normalized.contains("/src/test/")
                || normalized.contains("/test/")
                || normalized.endsWith("SecurityScanServiceTest.java")
                || normalized.endsWith("QualityAnalysisServiceTest.java");
    }

    private List<Finding> applyOwaspRules(String filePath, String content) {
        List<Finding> findings = new ArrayList<>();
        String sanitizedContent = stripStringLiteralsAndComments(content);
        String[] lines = sanitizedContent.split("\\R", -1);

        for (SecurityRule rule : OWASP_RULES) {
            for (int i = 0; i < lines.length; i++) {
                if (rule.pattern().matcher(lines[i]).find()) {
                    findings.add(buildFinding(rule, filePath, i + 1));
                }
            }

            Matcher multiLineMatcher = rule.pattern().matcher(sanitizedContent);
            while (multiLineMatcher.find()) {
                int lineNum = lineNumberOf(sanitizedContent, multiLineMatcher.start());

                boolean alreadyFound = findings.stream().anyMatch(f ->
                        rule.ruleId().equals(f.getRuleId())
                                && Objects.equals(filePath, f.getFilePath())
                                && Objects.equals(lineNum, f.getLineNumber())
                );

                if (!alreadyFound) {
                    findings.add(buildFinding(rule, filePath, lineNum));
                }
            }
        }

        return findings;
    }

    private String stripStringLiteralsAndComments(String content) {
        StringBuilder result = new StringBuilder(content.length());

        boolean inString = false;
        boolean inChar = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean escaped = false;

        for (int i = 0; i < content.length(); i++) {
            char current = content.charAt(i);
            char next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (current == '\n') {
                    inLineComment = false;
                    result.append('\n');
                } else {
                    result.append(' ');
                }
                continue;
            }

            if (inBlockComment) {
                if (current == '*' && next == '/') {
                    inBlockComment = false;
                    result.append("  ");
                    i++;
                } else if (current == '\n') {
                    result.append('\n');
                } else {
                    result.append(' ');
                }
                continue;
            }

            if (inString) {
                if (current == '\n') {
                    inString = false;
                    result.append('\n');
                } else {
                    result.append(' ');
                    if (current == '"' && !escaped) {
                        inString = false;
                    }
                    escaped = current == '\\' && !escaped;
                    if (current != '\\') {
                        escaped = false;
                    }
                }
                continue;
            }

            if (inChar) {
                if (current == '\n') {
                    inChar = false;
                    result.append('\n');
                } else {
                    result.append(' ');
                    if (current == '\'' && !escaped) {
                        inChar = false;
                    }
                    escaped = current == '\\' && !escaped;
                    if (current != '\\') {
                        escaped = false;
                    }
                }
                continue;
            }

            if (current == '/' && next == '/') {
                inLineComment = true;
                result.append("  ");
                i++;
            } else if (current == '/' && next == '*') {
                inBlockComment = true;
                result.append("  ");
                i++;
            } else if (current == '"') {
                inString = true;
                escaped = false;
                result.append(' ');
            } else if (current == '\'') {
                inChar = true;
                escaped = false;
                result.append(' ');
            } else {
                result.append(current);
            }
        }

        return result.toString();
    }

    private Finding buildFinding(SecurityRule rule, String filePath, int lineNumber) {
        return Finding.builder()
                .severity(rule.severity())
                .category(Category.SECURITY)
                .title(rule.title())
                .description(rule.description())
                .recommendation(rule.recommendation())
                .filePath(filePath)
                .lineNumber(lineNumber)
                .ruleId(rule.ruleId())
                .source("OWASP_SCANNER")
                .build();
    }

    private int lineNumberOf(String content, int charOffset) {
        if (content == null || content.isEmpty() || charOffset <= 0) {
            return 1;
        }

        int safeOffset = Math.min(charOffset, content.length());

        return (int) content.substring(0, safeOffset)
                .chars()
                .filter(c -> c == '\n')
                .count() + 1;
    }

    private List<Finding> deduplicate(List<Finding> findings) {
        Set<String> seen = new LinkedHashSet<>();
        List<Finding> result = new ArrayList<>();

        for (Finding finding : findings) {
            String key = finding.getRuleId()
                    + "|"
                    + finding.getFilePath()
                    + "|"
                    + finding.getLineNumber();

            if (seen.add(key)) {
                result.add(finding);
            }
        }

        return result;
    }

    private String normalizePath(String path) {
        return path == null ? "" : path.replace("\\", "/");
    }
}
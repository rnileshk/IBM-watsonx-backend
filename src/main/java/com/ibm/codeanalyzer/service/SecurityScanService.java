package com.ibm.codeanalyzer.service;

import com.ibm.codeanalyzer.model.Finding;
import com.ibm.codeanalyzer.model.Finding.Category;
import com.ibm.codeanalyzer.model.Finding.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

/**
 * Security Scanning Service
 *
 * Layer 1: Built-in regex-based OWASP rule engine (always runs, zero deps).
 * Layer 2: Semgrep CLI subprocess (optional, enabled via config).
 *
 * Covers the OWASP Top 10 most common issues in monolith Java code:
 *   A01 - Broken Access Control
 *   A02 - Cryptographic Failures
 *   A03 - Injection (SQL, Command, LDAP, XPATH)
 *   A04 - Insecure Design (hardcoded secrets)
 *   A05 - Security Misconfiguration
 *   A07 - Identification and Authentication Failures
 *   A08 - Software and Data Integrity (deserialization)
 *   A09 - Security Logging and Monitoring Failures
 */
@Slf4j
@Service
public class SecurityScanService {

    @Value("${security.semgrep.enabled:false}")
    private boolean semgrepEnabled;

    @Value("${security.semgrep.binary:semgrep}")
    private String semgrepBinary;

    // ─────────────────────────────────────────────────────────────────
    //  OWASP Rule Definitions
    // ─────────────────────────────────────────────────────────────────

    private record SecurityRule(
            String ruleId,
            String title,
            String description,
            String recommendation,
            Severity severity,
            Pattern pattern
    ) {}

    private static final List<SecurityRule> OWASP_RULES = List.of(

        // ── A03: SQL Injection ─────────────────────────────────────────
        new SecurityRule(
            "OWASP-A03-SQL-001",
            "Potential SQL Injection",
            "String concatenation used to build a SQL query. User input may reach this directly.",
            "Use PreparedStatement with parameterized queries. Never concatenate user input into SQL strings.",
            Severity.CRITICAL,
            Pattern.compile("(Statement|createStatement).*execute.*\\+|\"SELECT.*\"\\s*\\+|\"INSERT.*\"\\s*\\+|\"UPDATE.*\"\\s*\\+|\"DELETE.*\"\\s*\\+", Pattern.CASE_INSENSITIVE)
        ),

        // ── A03: Command Injection ─────────────────────────────────────
        new SecurityRule(
            "OWASP-A03-CMD-001",
            "Potential Command Injection",
            "Runtime.exec() or ProcessBuilder invoked with a value that may include user input.",
            "Validate and sanitize all input before passing to system commands. Use an allowlist of permitted commands.",
            Severity.CRITICAL,
            Pattern.compile("Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder.*\\+", Pattern.CASE_INSENSITIVE)
        ),

        // ── A02: Hardcoded Password ────────────────────────────────────
        new SecurityRule(
            "OWASP-A02-CRED-001",
            "Hardcoded Password or Secret",
            "A hardcoded password, secret, or API key was found. This leaks credentials in source control.",
            "Use environment variables, a secrets manager (e.g. HashiCorp Vault, AWS Secrets Manager), or Spring Boot externalized configuration.",
            Severity.HIGH,
            Pattern.compile("(password|passwd|secret|api[_-]?key|access[_-]?token)\\s*=\\s*\"[^\"]{4,}\"", Pattern.CASE_INSENSITIVE)
        ),

        // ── A02: Weak Cryptography ─────────────────────────────────────
        new SecurityRule(
            "OWASP-A02-CRYPTO-001",
            "Weak Cryptographic Algorithm",
            "MD5 or SHA-1 are cryptographically broken and must not be used for passwords or security-sensitive hashing.",
            "Use SHA-256 or stronger for integrity checks. For passwords, use BCrypt, Argon2, or PBKDF2.",
            Severity.HIGH,
            Pattern.compile("MessageDigest\\.getInstance\\(\"(MD5|SHA-1|SHA1)\"\\)", Pattern.CASE_INSENSITIVE)
        ),

        // ── A02: Insecure Random ───────────────────────────────────────
        new SecurityRule(
            "OWASP-A02-RAND-001",
            "Insecure Random Number Generator",
            "java.util.Random is not cryptographically secure and must not be used for tokens, session IDs, or security-sensitive values.",
            "Replace java.util.Random with java.security.SecureRandom for any security-sensitive context.",
            Severity.MEDIUM,
            Pattern.compile("new Random\\(\\)|Math\\.random\\(\\)", Pattern.CASE_INSENSITIVE)
        ),

        // ── A08: Unsafe Deserialization ────────────────────────────────
        new SecurityRule(
            "OWASP-A08-DESER-001",
            "Unsafe Java Deserialization",
            "ObjectInputStream.readObject() can execute arbitrary code if the serialized payload is attacker-controlled.",
            "Avoid Java native deserialization for untrusted data. Use a safe format (JSON, Protobuf). If deserialization is necessary, implement a look-ahead ObjectInputStream or use serialization filters (JEP 290).",
            Severity.CRITICAL,
            Pattern.compile("ObjectInputStream|readObject\\(\\)", Pattern.CASE_INSENSITIVE)
        ),

        // ── A01: Missing Authorization Check ──────────────────────────
        new SecurityRule(
            "OWASP-A01-AUTH-001",
            "Missing Authorization Annotation",
            "A public @RequestMapping/@GetMapping/@PostMapping endpoint is missing @PreAuthorize, @Secured, or @RolesAllowed.",
            "Add appropriate authorization annotations or configure HttpSecurity rules to restrict access to sensitive endpoints.",
            Severity.HIGH,
            Pattern.compile("@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)[^;]{0,200}(?!@(PreAuthorize|Secured|RolesAllowed))", Pattern.DOTALL)
        ),

        // ── A05: Disabled CSRF ─────────────────────────────────────────
        new SecurityRule(
            "OWASP-A05-CSRF-001",
            "CSRF Protection Disabled",
            "csrf().disable() turns off Spring Security's Cross-Site Request Forgery protection.",
            "Enable CSRF protection for all state-changing operations, or use the SameSite cookie attribute with a stateless JWT strategy.",
            Severity.HIGH,
            Pattern.compile("\\.csrf\\(\\s*\\)\\s*\\.disable\\(\\)|csrf\\.disable", Pattern.CASE_INSENSITIVE)
        ),

        // ── A05: SSL Verification Disabled ────────────────────────────
        new SecurityRule(
            "OWASP-A05-TLS-001",
            "SSL/TLS Certificate Validation Disabled",
            "TrustAllCerts / setSSLContext with a no-op TrustManager disables certificate validation entirely.",
            "Use a properly configured TrustManager with a valid certificate chain. Never disable certificate validation in production.",
            Severity.CRITICAL,
            Pattern.compile("TrustAllCerts|setHostnameVerifier|ALLOW_ALL_HOSTNAME_VERIFIER|trustAllCerts|X509TrustManager.*checkServer", Pattern.CASE_INSENSITIVE)
        ),

        // ── A07: Plaintext Password Comparison ────────────────────────
        new SecurityRule(
            "OWASP-A07-PWD-001",
            "Plaintext Password Comparison",
            "Passwords compared directly with equals() are stored or transmitted in plaintext.",
            "Hash passwords with BCrypt/Argon2 on storage and use a constant-time comparison method (e.g. MessageDigest.isEqual) to prevent timing attacks.",
            Severity.CRITICAL,
            Pattern.compile("password\\.equals\\(|equals\\(password|getPassword\\(\\)\\.equals", Pattern.CASE_INSENSITIVE)
        ),

        // ── A09: Logging Sensitive Data ────────────────────────────────
        new SecurityRule(
            "OWASP-A09-LOG-001",
            "Sensitive Data Logged",
            "Password, token, or secret-related variables are passed to a logger, which may write them to persistent log files.",
            "Never log passwords, tokens, or secrets. Use structured logging with field-level masking, or redact sensitive fields before logging.",
            Severity.MEDIUM,
            Pattern.compile("log\\.(info|debug|warn|error)\\(.*password|log\\.(info|debug|warn|error)\\(.*token|log\\.(info|debug|warn|error)\\(.*secret", Pattern.CASE_INSENSITIVE)
        ),

        // ── A03: LDAP Injection ────────────────────────────────────────
        new SecurityRule(
            "OWASP-A03-LDAP-001",
            "Potential LDAP Injection",
            "LDAP filter constructed with string concatenation. Attacker-controlled input can alter the LDAP query.",
            "Use JNDI or an LDAP library that supports parameterized filters, or sanitize input using OWASP's LDAP encoding library.",
            Severity.HIGH,
            Pattern.compile("DirContext|search\\(.*\\+|LdapTemplate.*\\+", Pattern.CASE_INSENSITIVE)
        ),

        // ── A03: XXE (XML External Entity) ────────────────────────────
        new SecurityRule(
            "OWASP-A03-XXE-001",
            "XML External Entity (XXE) Risk",
            "DocumentBuilder or SAXParser created without disabling external entity processing.",
            "Disable DOCTYPE declarations and external entity resolution: factory.setFeature(\"http://apache.org/xml/features/disallow-doctype-decl\", true)",
            Severity.HIGH,
            Pattern.compile("DocumentBuilderFactory\\.newInstance\\(\\)|SAXParserFactory\\.newInstance\\(\\)|XMLInputFactory\\.newInstance\\(\\)", Pattern.CASE_INSENSITIVE)
        )
    );

    // ─────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * Run all security checks against the provided source map and return findings.
     *
     * @param sources Map of { relativePath → fileContent }
     */
    public List<Finding> scan(Map<String, String> sources) {
        log.info("Starting security scan on {} files", sources.size());
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String filePath = entry.getKey();
            String content  = entry.getValue();

            findings.addAll(applyOwaspRules(filePath, content));
        }

        if (semgrepEnabled) {
            // Semgrep runs against the local filesystem — callers pass the file paths
            log.info("Semgrep integration enabled but requires filesystem paths; skipping in-memory scan.");
        }

        // Deduplicate by (ruleId + filePath + lineNumber)
        List<Finding> deduped = deduplicate(findings);

        log.info("Security scan complete — {} findings ({} after dedup)",
                findings.size(), deduped.size());
        return deduped;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────

    private List<Finding> applyOwaspRules(String filePath, String content) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = content.split("\n");

        for (SecurityRule rule : OWASP_RULES) {
            // Line-by-line scan for precise line numbers
            for (int i = 0; i < lines.length; i++) {
                if (rule.pattern().matcher(lines[i]).find()) {
                    findings.add(Finding.builder()
                            .severity(rule.severity())
                            .category(Category.SECURITY)
                            .title(rule.title())
                            .description(rule.description())
                            .recommendation(rule.recommendation())
                            .filePath(filePath)
                            .lineNumber(i + 1)
                            .ruleId(rule.ruleId())
                            .source("OWASP_SCANNER")
                            .build());
                }
            }

            // Also try multi-line match for patterns that span lines
            Matcher multiLineMatcher = rule.pattern().matcher(content);
            while (multiLineMatcher.find()) {
                int lineNum = lineNumberOf(content, multiLineMatcher.start());
                boolean alreadyFound = findings.stream().anyMatch(f ->
                        rule.ruleId().equals(f.getRuleId()) &&
                        filePath.equals(f.getFilePath()) &&
                        Integer.valueOf(lineNum).equals(f.getLineNumber()));

                if (!alreadyFound) {
                    findings.add(Finding.builder()
                            .severity(rule.severity())
                            .category(Category.SECURITY)
                            .title(rule.title())
                            .description(rule.description())
                            .recommendation(rule.recommendation())
                            .filePath(filePath)
                            .lineNumber(lineNum)
                            .ruleId(rule.ruleId())
                            .source("OWASP_SCANNER")
                            .build());
                }
            }
        }

        return findings;
    }

    private int lineNumberOf(String content, int charOffset) {
        return (int) content.substring(0, charOffset).chars()
                .filter(c -> c == '\n').count() + 1;
    }

    private List<Finding> deduplicate(List<Finding> findings) {
        Set<String> seen = new LinkedHashSet<>();
        List<Finding> result = new ArrayList<>();
        for (Finding f : findings) {
            String key = f.getRuleId() + "|" + f.getFilePath() + "|" + f.getLineNumber();
            if (seen.add(key)) result.add(f);
        }
        return result;
    }
}

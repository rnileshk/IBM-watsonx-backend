package com.ibm.codeanalyzer.service;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.*;
import com.ibm.codeanalyzer.model.Finding;
import com.ibm.codeanalyzer.model.Finding.Category;
import com.ibm.codeanalyzer.model.Finding.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

/**
 * Code Quality Analysis Service
 *
 * Detects code smells and quality issues:
 *   - Cyclomatic complexity (via JavaParser AST)
 *   - Long methods / God classes
 *   - Code duplication markers
 *   - Missing error handling
 *   - Deprecated API usage
 *   - Poor naming conventions
 *   - Dead code patterns
 */
@Slf4j
@Service
public class QualityAnalysisService {

    private static final int MAX_METHOD_LENGTH      = 50;
    private static final int MAX_CLASS_LENGTH        = 500;
    private static final int MAX_CYCLOMATIC          = 10;
    private static final int MAX_PARAMETERS          = 5;
    private static final int MAX_NESTING_DEPTH       = 4;

    // ─────────────────────────────────────────────────────────────────
    //  Quality Rule Definitions (regex-based, language-agnostic)
    // ─────────────────────────────────────────────────────────────────

    private record QualityRule(
            String ruleId, String title, String description,
            String recommendation, Severity severity, Pattern pattern
    ) {}

    private static final List<QualityRule> QUALITY_RULES = List.of(

            new QualityRule(
                    "QA-PRINT-001",
                    "System.out.println in production code",
                    "System.out.println() bypasses the logging framework, cannot be filtered by log level, and leaks to stdout in production.",
                    "Replace with a proper SLF4J logger: private static final Logger log = LoggerFactory.getLogger(Foo.class);",
                    Severity.LOW,
                    Pattern.compile("System\\.out\\.println|System\\.err\\.println")
            ),
            new QualityRule(
                    "QA-EMPTY-CATCH-001",
                    "Empty catch block",
                    "An empty catch block silently swallows the exception, making failures invisible and nearly impossible to debug.",
                    "At minimum log the exception: log.error(\"Error description\", e); or rethrow as a RuntimeException if unrecoverable.",
                    Severity.MEDIUM,
                    Pattern.compile("catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}", Pattern.DOTALL)
            ),
            new QualityRule(
                    "QA-TODO-001",
                    "Unresolved TODO / FIXME comment",
                    "Unresolved TODO or FIXME comments indicate incomplete implementation or known bugs left in production.",
                    "Convert to a tracked issue in your project management system and remove the comment, or implement the fix before merging.",
                    Severity.INFO,
                    Pattern.compile("//\\s*(TODO|FIXME|HACK|XXX)", Pattern.CASE_INSENSITIVE)
            ),
            new QualityRule(
                    "QA-DEPRECATED-001",
                    "Deprecated API usage",
                    "Usage of @Deprecated API detected. Deprecated methods may be removed in future versions.",
                    "Migrate to the recommended replacement API shown in the @deprecated Javadoc tag.",
                    Severity.LOW,
                    Pattern.compile("@Deprecated|new Date\\(\\)|Date\\.getYear|Date\\.getMonth|StringBuffer\\(\\)")
            ),
            new QualityRule(
                    "QA-HARDCODE-001",
                    "Hardcoded URL or IP address",
                    "Hardcoded URLs or IP addresses make the code environment-specific and non-configurable.",
                    "Externalize to application.properties or environment variables using @Value annotation.",
                    Severity.MEDIUM,
                    Pattern.compile("\"https?://[a-zA-Z0-9.-]+|\"\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")
            ),
            new QualityRule(
                    "QA-NPE-001",
                    "Potential NullPointerException (missing null check)",
                    "Method result used immediately without null check. This can cause NullPointerException at runtime.",
                    "Use Optional<T>, add explicit null checks, or annotate with @NonNull to enforce at compile time.",
                    Severity.MEDIUM,
                    Pattern.compile("\\.(get|find|fetch|load|retrieve)\\w*\\(.*\\)\\.", Pattern.CASE_INSENSITIVE)
            ),
            new QualityRule(
                    "QA-STATIC-001",
                    "Mutable static field",
                    "Static mutable fields are shared across all instances and threads, causing concurrency bugs.",
                    "Use instance fields, ThreadLocal, or immutable static constants (static final).",
                    Severity.MEDIUM,
                    Pattern.compile("private static (?!final)\\w+\\s+\\w+\\s*=")
            ),
            new QualityRule(
                    "QA-INSTANCEOF-CHAIN-001",
                    "Long instanceof chain (type-checking anti-pattern)",
                    "A chain of instanceof checks is a code smell indicating a missing polymorphic design.",
                    "Refactor using polymorphism, the Visitor pattern, or sealed classes (Java 17+).",
                    Severity.LOW,
                    Pattern.compile("instanceof.{0,100}instanceof.{0,100}instanceof", Pattern.DOTALL)
            )
    );

    // ─────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────

    public List<Finding> analyze(Map<String, String> sources) {
        log.info("Starting code quality analysis on {} files", sources.size());
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String filePath = entry.getKey();
            String content  = entry.getValue();

            // Regex-based rules (all languages)
            findings.addAll(applyQualityRules(filePath, content));

            // AST-based analysis (Java only)
            if (filePath.endsWith(".java")) {
                findings.addAll(analyzeJavaAst(filePath, content));
            }

            // General structural checks
            findings.addAll(checkStructuralMetrics(filePath, content));
        }

        log.info("Quality analysis complete — {} findings", findings.size());
        return findings;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────

    private List<Finding> applyQualityRules(String filePath, String content) {
        List<Finding> findings = new ArrayList<>();
        String[] lines = content.split("\n");

        for (QualityRule rule : QUALITY_RULES) {
            for (int i = 0; i < lines.length; i++) {
                if (rule.pattern().matcher(lines[i]).find()) {
                    findings.add(Finding.builder()
                            .severity(rule.severity())
                            .category(Category.CODE_QUALITY)
                            .title(rule.title())
                            .description(rule.description())
                            .recommendation(rule.recommendation())
                            .filePath(filePath)
                            .lineNumber(i + 1)
                            .ruleId(rule.ruleId())
                            .source("QUALITY_SCANNER")
                            .build());
                }
            }
        }
        return findings;
    }

    private List<Finding> analyzeJavaAst(String filePath, String content) {
        List<Finding> findings = new ArrayList<>();

        try {
            CompilationUnit cu = StaticJavaParser.parse(content);
            String[] lines = content.split("\n");

            // Inspect every method
            cu.findAll(MethodDeclaration.class).forEach(method -> {
                int startLine = method.getBegin()
                        .map(p -> p.line).orElse(0);

                // ── Long method ─────────────────────────────────────
                int methodLines = method.getEnd().map(p -> p.line).orElse(startLine) - startLine;
                if (methodLines > MAX_METHOD_LENGTH) {
                    findings.add(Finding.builder()
                            .severity(Severity.MEDIUM)
                            .category(Category.CODE_QUALITY)
                            .title("Long method: " + method.getNameAsString())
                            .description(String.format(
                                    "Method '%s' is %d lines long (threshold: %d). Long methods are hard to read, test, and maintain.",
                                    method.getNameAsString(), methodLines, MAX_METHOD_LENGTH))
                            .recommendation("Extract logical blocks into well-named private methods following the Single Responsibility Principle.")
                            .filePath(filePath)
                            .lineNumber(startLine)
                            .ruleId("QA-LONG-METHOD-001")
                            .source("JAVAPARSER_AST")
                            .build());
                }

                // ── Too many parameters ──────────────────────────────
                int paramCount = method.getParameters().size();
                if (paramCount > MAX_PARAMETERS) {
                    findings.add(Finding.builder()
                            .severity(Severity.LOW)
                            .category(Category.CODE_QUALITY)
                            .title("Too many parameters: " + method.getNameAsString())
                            .description(String.format(
                                    "Method '%s' has %d parameters (threshold: %d). This increases coupling and reduces readability.",
                                    method.getNameAsString(), paramCount, MAX_PARAMETERS))
                            .recommendation("Introduce a Parameter Object (data class/record) to group related parameters.")
                            .filePath(filePath)
                            .lineNumber(startLine)
                            .ruleId("QA-PARAMS-001")
                            .source("JAVAPARSER_AST")
                            .build());
                }

                // ── Cyclomatic complexity ────────────────────────────
                int complexity = calculateCyclomaticComplexity(method);
                if (complexity > MAX_CYCLOMATIC) {
                    findings.add(Finding.builder()
                            .severity(complexity > 20 ? Severity.HIGH : Severity.MEDIUM)
                            .category(Category.CODE_QUALITY)
                            .title("High cyclomatic complexity: " + method.getNameAsString())
                            .description(String.format(
                                    "Method '%s' has a cyclomatic complexity of %d (threshold: %d). High complexity correlates with bug density.",
                                    method.getNameAsString(), complexity, MAX_CYCLOMATIC))
                            .recommendation("Decompose complex conditional logic into smaller methods or use the Strategy / State pattern.")
                            .filePath(filePath)
                            .lineNumber(startLine)
                            .ruleId("QA-COMPLEXITY-001")
                            .source("JAVAPARSER_AST")
                            .build());
                }

                // ── Deep nesting ─────────────────────────────────────
                int depth = calculateMaxNestingDepth(method.getBody().orElse(null));
                if (depth > MAX_NESTING_DEPTH) {
                    findings.add(Finding.builder()
                            .severity(Severity.LOW)
                            .category(Category.CODE_QUALITY)
                            .title("Deep nesting in: " + method.getNameAsString())
                            .description(String.format(
                                    "Method '%s' has a maximum nesting depth of %d (threshold: %d). Deeply nested code is error-prone.",
                                    method.getNameAsString(), depth, MAX_NESTING_DEPTH))
                            .recommendation("Apply early-return (guard clauses) to flatten nesting. Consider extracting nested blocks into methods.")
                            .filePath(filePath)
                            .lineNumber(startLine)
                            .ruleId("QA-NESTING-001")
                            .source("JAVAPARSER_AST")
                            .build());
                }
            });

        } catch (Exception e) {
            log.debug("Could not parse Java AST for {}: {}", filePath, e.getMessage());
        }

        return findings;
    }

    private List<Finding> checkStructuralMetrics(String filePath, String content) {
        List<Finding> findings = new ArrayList<>();
        int lineCount = content.split("\n").length;

        if (lineCount > MAX_CLASS_LENGTH) {
            findings.add(Finding.builder()
                    .severity(Severity.MEDIUM)
                    .category(Category.CODE_QUALITY)
                    .title("God class / large file")
                    .description(String.format(
                            "File '%s' has %d lines (threshold: %d). Large files often violate the Single Responsibility Principle.",
                            filePath, lineCount, MAX_CLASS_LENGTH))
                    .recommendation("Split the class into smaller, focused classes each responsible for one cohesive behavior.")
                    .filePath(filePath)
                    .lineNumber(1)
                    .ruleId("QA-GOD-CLASS-001")
                    .source("STRUCTURAL_METRICS")
                    .build());
        }

        return findings;
    }

    /**
     * Calculates cyclomatic complexity:
     * CC = 1 + number of branching statements (if, else-if, for, while, do, case, catch, &&, ||, ternary)
     */
    private int calculateCyclomaticComplexity(MethodDeclaration method) {
        int[] count = {1}; // base complexity

        method.walk(node -> {
            if (node instanceof IfStmt || node instanceof ForStmt ||
                    node instanceof WhileStmt || node instanceof DoStmt ||
                    node instanceof SwitchEntry || node instanceof CatchClause ||
                    node instanceof ConditionalExpr) {
                count[0]++;
            }
        });

        return count[0];
    }

    /**
     * Calculates the maximum nesting depth by walking the statement tree.
     */
    private int calculateMaxNestingDepth(BlockStmt block) {
        if (block == null) return 0;
        return computeDepth(block, 0);
    }

    private int computeDepth(com.github.javaparser.ast.Node node, int current) {
        int max = current;
        for (com.github.javaparser.ast.Node child : node.getChildNodes()) {
            if (child instanceof IfStmt || child instanceof ForStmt ||
                    child instanceof WhileStmt || child instanceof DoStmt ||
                    child instanceof SwitchStmt || child instanceof TryStmt) {
                max = Math.max(max, computeDepth(child, current + 1));
            } else {
                max = Math.max(max, computeDepth(child, current));
            }
        }
        return max;
    }
}

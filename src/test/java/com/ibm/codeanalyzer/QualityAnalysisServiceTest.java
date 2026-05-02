package com.ibm.codeanalyzer;

import com.ibm.codeanalyzer.model.Finding;
import com.ibm.codeanalyzer.service.QualityAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class QualityAnalysisServiceTest {

    private QualityAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new QualityAnalysisService();
    }

    @Test
    @DisplayName("Detects System.out.println usage")
    void detectsSystemOutPrintln() {
        String code = "System.out.println(\"debug value: \" + someVar);";
        List<Finding> findings = service.analyze(Map.of("Foo.java", code));
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("QA-PRINT-001"));
    }

    @Test
    @DisplayName("Detects empty catch block")
    void detectsEmptyCatchBlock() {
        String code = """
            try {
                doSomething();
            } catch (Exception e) {}
            """;
        List<Finding> findings = service.analyze(Map.of("Foo.java", code));
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("QA-EMPTY-CATCH-001"));
    }

    @Test
    @DisplayName("Detects TODO comment")
    void detectsTodoComment() {
        String code = "// TODO: implement retry logic\npublic void retry() {}";
        List<Finding> findings = service.analyze(Map.of("RetryService.java", code));
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("QA-TODO-001"));
    }

    @Test
    @DisplayName("Detects god class (large file)")
    void detectsGodClass() {
        // Generate a class with > 500 lines
        StringBuilder sb = new StringBuilder("public class BigClass {\n");
        for (int i = 0; i < 510; i++) {
            sb.append("    private int field").append(i).append(";\n");
        }
        sb.append("}");

        List<Finding> findings = service.analyze(Map.of("BigClass.java", sb.toString()));
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("QA-GOD-CLASS-001"));
    }

    @Test
    @DisplayName("Detects high cyclomatic complexity via AST")
    void detectsHighCyclomaticComplexity() {
        String code = """
            public class ComplexService {
                public String process(String input) {
                    if (input == null) return "null";
                    if (input.isEmpty()) return "empty";
                    if (input.startsWith("A")) {
                        if (input.length() > 10) return "long-A";
                        else return "short-A";
                    } else if (input.startsWith("B")) {
                        for (int i = 0; i < input.length(); i++) {
                            if (input.charAt(i) == 'x') return "has-x";
                        }
                        return "B-no-x";
                    } else if (input.startsWith("C")) {
                        return "C";
                    } else if (input.startsWith("D")) {
                        return "D";
                    } else if (input.startsWith("E")) {
                        return "E";
                    }
                    return "other";
                }
            }
            """;

        List<Finding> findings = service.analyze(Map.of("ComplexService.java", code));
        assertThat(findings).anyMatch(f -> f.getRuleId().equals("QA-COMPLEXITY-001"));
    }
}

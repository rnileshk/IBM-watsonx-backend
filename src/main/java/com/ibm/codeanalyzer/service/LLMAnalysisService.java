package com.ibm.codeanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.ibm.codeanalyzer.model.CodeChunk;
import com.ibm.codeanalyzer.model.Finding;
import com.ibm.codeanalyzer.model.Finding.Category;
import com.ibm.codeanalyzer.model.Finding.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;

/**
 * LLM Analysis Service
 *
 * Sends chunked source code to a configured LLM provider and interprets the
 * response as structured architectural findings.
 *
 * Supported providers (configured via llm.provider):
 *   - anthropic  (default — Claude Sonnet)
 *   - openai     (GPT-4o)
 *   - watsonx    (IBM Granite — ideal for IBM Dev Bob Challenge)
 *
 * Strategy:
 *   1. Each chunk is analyzed individually for local patterns.
 *   2. A final "project-level" prompt sends a meta-summary asking for
 *      architectural observations that transcend individual files.
 */
@Slf4j
@Service
public class LLMAnalysisService {

    private final WebClient anthropicClient;
    private final WebClient openaiClient;
    private final WebClient watsonxClient;
    private final ObjectMapper objectMapper;

    @Value("${llm.provider:anthropic}")
    private String provider;

    @Value("${llm.max-tokens:2000}")
    private int maxTokens;

    @Value("${llm.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${llm.watsonx.model-id:ibm/granite-13b-instruct-v2}")
    private String watsonxModelId;

    @Value("${llm.watsonx.project-id:}")
    private String watsonxProjectId;

    @Value("${llm.openai.model:gpt-4o}")
    private String openaiModel;

    @Value("${llm.anthropic.model:claude-sonnet-4-20250514}")
    private String anthropicModel;

    public LLMAnalysisService(
            @Qualifier("anthropicClient") WebClient anthropicClient,
            @Qualifier("openaiClient")    WebClient openaiClient,
            @Qualifier("watsonxClient")   WebClient watsonxClient,
            ObjectMapper objectMapper) {
        this.anthropicClient = anthropicClient;
        this.openaiClient    = openaiClient;
        this.watsonxClient   = watsonxClient;
        this.objectMapper    = objectMapper;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────

    /**
     * Analyze a list of code chunks and return architecture/improvement findings.
     *
     * @param chunks     Chunked source code from IngestionService
     * @param projectName Optional project name for better LLM context
     */
    public LLMAnalysisOutput analyze(List<CodeChunk> chunks, String projectName) {
        log.info("Starting LLM analysis with provider={}, chunks={}", provider, chunks.size());

        List<Finding> findings = new ArrayList<>();
        List<String> chunkSummaries = new ArrayList<>();

        // ── Phase 1: Per-chunk analysis ───────────────────────────────
        for (CodeChunk chunk : chunks) {
            try {
                String chunkPrompt = buildChunkPrompt(chunk, projectName);
                String response = callLLM(chunkPrompt);
                ChunkAnalysis analysis = parseChunkAnalysis(response, chunk.getFilePath());
                findings.addAll(analysis.findings());
                chunkSummaries.add(analysis.summary());
            } catch (Exception e) {
                log.warn("LLM chunk analysis failed for {}: {}", chunk.getFilePath(), e.getMessage());
            }
        }

        // ── Phase 2: Project-level architectural summary ───────────────
        String executiveSummary = "";
        String architecturalAssessment = "";
        List<String> topRecommendations = new ArrayList<>();

        try {
            String metaPrompt = buildMetaPrompt(chunkSummaries, projectName);
            String metaResponse = callLLM(metaPrompt);
            MetaAnalysis meta = parseMetaAnalysis(metaResponse);
            executiveSummary         = meta.executiveSummary();
            architecturalAssessment  = meta.architecturalAssessment();
            topRecommendations       = meta.topRecommendations();
        } catch (Exception e) {
            log.warn("LLM meta-analysis failed: {}", e.getMessage());
            executiveSummary = "LLM meta-analysis could not complete: " + e.getMessage();
        }

        log.info("LLM analysis complete — {} findings", findings.size());
        return new LLMAnalysisOutput(findings, executiveSummary, architecturalAssessment, topRecommendations);
    }

    // ─────────────────────────────────────────────────────────────────
    //  Prompt builders
    // ─────────────────────────────────────────────────────────────────

    private String buildChunkPrompt(CodeChunk chunk, String projectName) {
        return """
            You are a senior software architect reviewing legacy monolith code for the IBM Dev Bob Challenge.

            Project: %s
            File: %s (chunk %d of %d, lines %d-%d)
            Language: %s

            Analyze the following source code for:
            1. Architectural anti-patterns (tight coupling, missing abstraction layers, violation of SOLID principles)
            2. Performance issues (N+1 queries, inefficient loops, resource leaks)
            3. Maintainability problems (code duplication, poor naming, missing abstractions)
            4. Dependency management issues
            5. Opportunities for modernization (monolith to microservices extraction candidates)

            Respond ONLY in this JSON format (no markdown, no preamble):
            {
              "summary": "one sentence summary of this file's role",
              "findings": [
                {
                  "title": "short title",
                  "description": "what the problem is",
                  "recommendation": "how to fix it",
                  "severity": "HIGH|MEDIUM|LOW|INFO",
                  "lineHint": 42
                }
              ]
            }

            Source code:
            ```%s
            %s
            ```
            """.formatted(
                    Optional.ofNullable(projectName).orElse("Unknown"),
                    chunk.getFilePath(),
                    chunk.getChunkIndex() + 1,
                    chunk.getTotalChunks(),
                    chunk.getStartLine(),
                    chunk.getEndLine(),
                    chunk.getLanguage(),
                    chunk.getLanguage(),
                    chunk.getContent()
        );
    }

    private String buildMetaPrompt(List<String> summaries, String projectName) {
        String joinedSummaries = String.join("\n- ", summaries);
        return """
            You are a senior software architect reviewing the overall architecture of a monolith codebase for the IBM Dev Bob Challenge.

            Project: %s

            Below are per-file summaries from scanning the codebase:
            - %s

            Based on these summaries, provide a high-level architectural assessment.

            Respond ONLY in this JSON format (no markdown, no preamble):
            {
              "executiveSummary": "2-3 sentence non-technical summary for stakeholders",
              "architecturalAssessment": "technical paragraph describing the overall architecture, its strengths and weaknesses",
              "topRecommendations": [
                "Recommendation 1 with specific action",
                "Recommendation 2 with specific action",
                "Recommendation 3 with specific action",
                "Recommendation 4 with specific action",
                "Recommendation 5 with specific action"
              ]
            }
            """.formatted(
                    Optional.ofNullable(projectName).orElse("Unknown"),
                    joinedSummaries
        );
    }

    // ─────────────────────────────────────────────────────────────────
    //  LLM Provider routing
    // ─────────────────────────────────────────────────────────────────

    private String callLLM(String prompt) {
        return switch (provider.toLowerCase()) {
            case "watsonx"   -> callWatsonx(prompt);
            case "openai"    -> callOpenAI(prompt);
            case "anthropic" -> callAnthropic(prompt);
            default -> throw new IllegalStateException("Unknown LLM provider: " + provider);
        };
    }

    // ── Anthropic Claude ──────────────────────────────────────────────
    private String callAnthropic(String prompt) {
        log.debug("Calling Anthropic API, model={}", anthropicModel);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", anthropicModel);
        body.put("max_tokens", maxTokens);

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);

        try {
            String responseBody = anthropicClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("content").get(0).path("text").asText();
        } catch (WebClientResponseException e) {
            log.error("Anthropic API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Anthropic API call failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Anthropic call failed: " + e.getMessage(), e);
        }
    }

    // ── OpenAI GPT ────────────────────────────────────────────────────
    private String callOpenAI(String prompt) {
        log.debug("Calling OpenAI API, model={}", openaiModel);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", openaiModel);
        body.put("max_tokens", maxTokens);

        ArrayNode messages = body.putArray("messages");
        ObjectNode msg = messages.addObject();
        msg.put("role", "user");
        msg.put("content", prompt);

        try {
            String responseBody = openaiClient.post()
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (WebClientResponseException e) {
            log.error("OpenAI API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("OpenAI API call failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("OpenAI call failed: " + e.getMessage(), e);
        }
    }

    // ── IBM watsonx ───────────────────────────────────────────────────
    private String callWatsonx(String prompt) {
        log.debug("Calling IBM watsonx API, model={}", watsonxModelId);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model_id", watsonxModelId);
        body.put("project_id", watsonxProjectId);
        body.put("input", prompt);

        ObjectNode params = body.putObject("parameters");
        params.put("max_new_tokens", maxTokens);
        params.put("temperature", 0.2);
        params.put("top_p", 0.9);

        try {
            String responseBody = watsonxClient.post()
                    .uri("?version=2023-05-29")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            JsonNode root = objectMapper.readTree(responseBody);
            return root.path("results").get(0).path("generated_text").asText();
        } catch (WebClientResponseException e) {
            log.error("watsonx API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("watsonx API call failed: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("watsonx call failed: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    //  Response parsers
    // ─────────────────────────────────────────────────────────────────

    private ChunkAnalysis parseChunkAnalysis(String rawResponse, String filePath) {
        try {
            // Strip markdown fences if present
            String json = rawResponse
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(json);
            String summary = root.path("summary").asText("No summary available");

            List<Finding> findings = new ArrayList<>();
            JsonNode findingsNode = root.path("findings");

            if (findingsNode.isArray()) {
                for (JsonNode f : findingsNode) {
                    Severity sev = parseSeverity(f.path("severity").asText("INFO"));
                    Integer lineHint = f.has("lineHint") ? f.path("lineHint").asInt() : null;

                    findings.add(Finding.builder()
                            .severity(sev)
                            .category(Category.ARCHITECTURE)
                            .title(f.path("title").asText())
                            .description(f.path("description").asText())
                            .recommendation(f.path("recommendation").asText())
                            .filePath(filePath)
                            .lineNumber(lineHint)
                            .ruleId("LLM-ARCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                            .source("LLM_" + provider.toUpperCase())
                            .build());
                }
            }

            return new ChunkAnalysis(summary, findings);
        } catch (Exception e) {
            log.warn("Failed to parse LLM chunk response: {}", e.getMessage());
            return new ChunkAnalysis("Could not parse LLM response", List.of());
        }
    }

    private MetaAnalysis parseMetaAnalysis(String rawResponse) {
        try {
            String json = rawResponse
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode root = objectMapper.readTree(json);

            List<String> recs = new ArrayList<>();
            JsonNode recsNode = root.path("topRecommendations");
            if (recsNode.isArray()) {
                recsNode.forEach(r -> recs.add(r.asText()));
            }

            return new MetaAnalysis(
                    root.path("executiveSummary").asText(),
                    root.path("architecturalAssessment").asText(),
                    recs
            );
        } catch (Exception e) {
            log.warn("Failed to parse LLM meta response: {}", e.getMessage());
            return new MetaAnalysis(
                    "Meta-analysis parsing failed.",
                    "Could not generate architectural assessment.",
                    List.of()
            );
        }
    }

    private Severity parseSeverity(String s) {
        return switch (s.toUpperCase()) {
            case "CRITICAL" -> Severity.CRITICAL;
            case "HIGH"     -> Severity.HIGH;
            case "MEDIUM"   -> Severity.MEDIUM;
            case "LOW"      -> Severity.LOW;
            default         -> Severity.INFO;
        };
    }

    // ─────────────────────────────────────────────────────────────────
    //  Internal result records
    // ─────────────────────────────────────────────────────────────────

    public record LLMAnalysisOutput(
            List<Finding>  findings,
            String         executiveSummary,
            String         architecturalAssessment,
            List<String>   topRecommendations
    ) {}

    private record ChunkAnalysis(String summary, List<Finding> findings) {}

    private record MetaAnalysis(
            String executiveSummary,
            String architecturalAssessment,
            List<String> topRecommendations
    ) {}
}

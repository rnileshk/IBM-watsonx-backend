# IBM Code Analyzer — Dev Bob Challenge

> **Spring Boot application** that reads an existing monolith codebase and provides a structured security vulnerability report + architectural improvement suggestions powered by an LLM (IBM watsonx / OpenAI / Anthropic Claude).

---

## Architecture

```
Client
  └── POST /api/v1/analyze/{url|code|upload}
        └── AnalysisController
              └── IngestionService  ← Git clone / ZIP extract / raw paste
                    └── AnalysisOrchestrator  ← parallel fan-out
                          ├── SecurityScanService   (OWASP rules, SAST)
                          ├── QualityAnalysisService (PMD rules, JavaParser AST)
                          └── LLMAnalysisService    (watsonx / OpenAI / Claude)
                                └── GET /api/v1/analyze/{jobId}  ← poll result
```

---

## Features

### Security Scanning (OWASP Top 10)
| Rule ID | Vulnerability | Severity |
|---|---|---|
| OWASP-A03-SQL-001 | SQL Injection (string concat) | CRITICAL |
| OWASP-A03-CMD-001 | Command Injection | CRITICAL |
| OWASP-A02-CRED-001 | Hardcoded passwords / secrets | HIGH |
| OWASP-A02-CRYPTO-001 | Weak crypto (MD5, SHA-1) | HIGH |
| OWASP-A02-RAND-001 | Insecure Random | MEDIUM |
| OWASP-A08-DESER-001 | Unsafe deserialization | CRITICAL |
| OWASP-A01-AUTH-001 | Missing authorization annotations | HIGH |
| OWASP-A05-CSRF-001 | Disabled CSRF protection | HIGH |
| OWASP-A05-TLS-001 | SSL/TLS validation disabled | CRITICAL |
| OWASP-A07-PWD-001 | Plaintext password comparison | CRITICAL |
| OWASP-A09-LOG-001 | Sensitive data logged | MEDIUM |
| OWASP-A03-LDAP-001 | LDAP Injection | HIGH |
| OWASP-A03-XXE-001 | XML External Entity | HIGH |

### Code Quality Analysis
- Cyclomatic complexity per method (via JavaParser AST)
- Long methods and god classes
- Deep nesting detection
- Too many parameters
- Empty catch blocks, unresolved TODOs
- System.out.println, mutable statics
- Hardcoded URLs / IPs

### LLM Architectural Analysis
- Per-file analysis with chunk-aware context window management
- Project-level meta-analysis: executive summary + architectural assessment
- Top 5 prioritized recommendations
- Supports **IBM watsonx** (Granite), **OpenAI GPT-4o**, and **Anthropic Claude**

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+

### 1. Clone and configure

```bash
git clone <this-repo>
cd code-analyzer
```

Edit `src/main/resources/application.properties`:

```properties
# Choose your LLM provider
llm.provider=anthropic   # or watsonx / openai

# Anthropic
llm.anthropic.api-key=sk-ant-YOUR_KEY

# IBM watsonx
llm.watsonx.api-key=YOUR_WATSONX_KEY
llm.watsonx.project-id=YOUR_PROJECT_ID

# OpenAI
llm.openai.api-key=sk-YOUR_KEY
```

### 2. Build and run

```bash
mvn spring-boot:run
```

### 3. Analyze a Git repository

```bash
curl -X POST http://localhost:8080/api/v1/analyze/url \
  -H "Content-Type: application/json" \
  -d '{
    "gitUrl": "https://github.com/your-org/monolith-app",
    "projectName": "Legacy Monolith",
    "includeLlmAnalysis": true
  }'
```

Response:
```json
{ "jobId": "abc-123", "status": "RUNNING", "poll": "/api/v1/analyze/abc-123" }
```

### 4. Poll for results

```bash
curl http://localhost:8080/api/v1/analyze/abc-123
```

### 5. Analyze raw code snippet

```bash
curl -X POST http://localhost:8080/api/v1/analyze/code \
  -H "Content-Type: application/json" \
  -d '{
    "code": "String sql = \"SELECT * FROM users WHERE id = \" + userId;",
    "fileName": "UserDao.java",
    "projectName": "Test",
    "includeLlmAnalysis": false
  }'
```

### 6. Upload a ZIP file

```bash
curl -X POST http://localhost:8080/api/v1/analyze/upload \
  -F "file=@myproject.zip" \
  -F "projectName=My Project"
```

---

## API Reference

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/v1/analyze/url` | Analyze a public Git repository |
| POST | `/api/v1/analyze/code` | Analyze raw pasted code |
| POST | `/api/v1/analyze/upload` | Analyze a ZIP or single file upload |
| GET | `/api/v1/analyze/{jobId}` | Poll status / retrieve full report |
| GET | `/api/v1/analyze/jobs` | List all job IDs |
| GET | `/api/v1/health` | Liveness probe |
| GET | `/actuator/health` | Spring Boot actuator health |

---

## Sample Report Structure

```json
{
  "jobId": "abc-123",
  "status": "COMPLETED",
  "projectName": "Legacy Monolith",
  "totalFindings": 47,
  "criticalCount": 3,
  "highCount": 12,
  "mediumCount": 18,
  "lowCount": 11,
  "infoCount": 3,
  "totalFilesScanned": 84,
  "totalLinesOfCode": 18420,
  "filesByLanguage": { "java": 78, "xml": 4, "yaml": 2 },
  "securityFindings": [ ... ],
  "qualityFindings": [ ... ],
  "architectureFindings": [ ... ],
  "executiveSummary": "The system shows significant technical debt...",
  "architecturalAssessment": "The codebase follows a classic three-tier MVC...",
  "topRecommendations": [
    "Migrate hardcoded DB credentials to Spring Boot externalized config",
    "Replace PreparedStatement concatenation with parameterized queries",
    ...
  ]
}
```

---

## Running Tests

```bash
mvn test
```

---

## Configuration Reference

| Property | Default | Description |
|---|---|---|
| `llm.provider` | `anthropic` | LLM provider: `anthropic`, `openai`, `watsonx` |
| `llm.max-tokens` | `2000` | Max tokens per LLM response |
| `llm.chunk-size-chars` | `8000` | Characters per code chunk |
| `llm.timeout-seconds` | `60` | LLM API call timeout |
| `analyzer.async.core-pool-size` | `4` | Thread pool core size |
| `analyzer.work-dir` | `/tmp/code-analyzer` | Temp directory for cloned repos |
| `security.semgrep.enabled` | `false` | Enable Semgrep CLI integration |

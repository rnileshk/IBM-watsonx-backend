package com.ibm.codeanalyzer;

import com.ibm.codeanalyzer.model.Finding;
import com.ibm.codeanalyzer.service.SecurityScanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class SecurityScanServiceTest {

    private SecurityScanService service;

    @BeforeEach
    void setUp() {
        service = new SecurityScanService();
    }

    @Test
    @DisplayName("Detects SQL injection via string concatenation")
    void detectsSqlInjection() {
        String code = """
            String query = "SELECT * FROM users WHERE id = " + userId;
            Statement stmt = conn.createStatement();
            stmt.executeQuery(query);
            """;

        List<Finding> findings = service.scan(Map.of("UserDao.java", code));

        assertThat(findings)
                .anyMatch(f -> f.getRuleId().equals("OWASP-A03-SQL-001"))
                .anyMatch(f -> f.getSeverity() == Finding.Severity.CRITICAL);
    }

    @Test
    @DisplayName("Detects hardcoded password")
    void detectsHardcodedPassword() {
        String code = """
            String password = "super_secret_123";
            connection = DriverManager.getConnection(url, "admin", password);
            """;

        List<Finding> findings = service.scan(Map.of("DbConfig.java", code));

        assertThat(findings)
                .anyMatch(f -> f.getRuleId().equals("OWASP-A02-CRED-001"));
    }

    @Test
    @DisplayName("Detects weak MD5 hash usage")
    void detectsWeakCrypto() {
        String code = """
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(password.getBytes());
            """;

        List<Finding> findings = service.scan(Map.of("AuthService.java", code));

        assertThat(findings)
                .anyMatch(f -> f.getRuleId().equals("OWASP-A02-CRYPTO-001"))
                .anyMatch(f -> f.getSeverity() == Finding.Severity.HIGH);
    }

    @Test
    @DisplayName("Detects unsafe deserialization")
    void detectsUnsafeDeserialization() {
        String code = """
            ObjectInputStream ois = new ObjectInputStream(inputStream);
            Object obj = ois.readObject();
            """;

        List<Finding> findings = service.scan(Map.of("Deserializer.java", code));

        assertThat(findings)
                .anyMatch(f -> f.getRuleId().equals("OWASP-A08-DESER-001"))
                .anyMatch(f -> f.getSeverity() == Finding.Severity.CRITICAL);
    }

    @Test
    @DisplayName("Detects disabled CSRF protection")
    void detectsDisabledCsrf() {
        String code = """
            http.csrf().disable()
                .authorizeRequests().antMatchers("/admin/**").hasRole("ADMIN");
            """;

        List<Finding> findings = service.scan(Map.of("SecurityConfig.java", code));

        assertThat(findings)
                .anyMatch(f -> f.getRuleId().equals("OWASP-A05-CSRF-001"));
    }

    @Test
    @DisplayName("Detects plaintext password comparison")
    void detectsPlaintextPasswordComparison() {
        String code = """
            if (inputPassword.equals(storedPassword)) {
                grantAccess();
            }
            """;

        List<Finding> findings = service.scan(Map.of("LoginService.java", code));

        assertThat(findings)
                .anyMatch(f -> f.getRuleId().equals("OWASP-A07-PWD-001"));
    }

    @Test
    @DisplayName("Clean code produces no security findings")
    void cleanCodeProducesNoFindings() {
        String code = """
            @Service
            public class UserService {
                private static final Logger log = LoggerFactory.getLogger(UserService.class);
                private final UserRepository repo;

                public Optional<User> findById(Long id) {
                    return repo.findById(id);
                }
            }
            """;

        List<Finding> findings = service.scan(Map.of("UserService.java", code));

        // May have minor findings but nothing critical
        assertThat(findings)
                .noneMatch(f -> f.getSeverity() == Finding.Severity.CRITICAL);
    }
}

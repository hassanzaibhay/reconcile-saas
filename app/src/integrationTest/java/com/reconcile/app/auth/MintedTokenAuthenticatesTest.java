package com.reconcile.app.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.reconcile.app.support.TenantTest;
import com.reconcile.shared.domain.TenantId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Delta 5 proof: a token minted with the exact HS256 scheme of {@code tools/mint-jwt.py} passes the real
 * {@code NimbusJwtDecoder} (signature + exp validators) and its {@code tid} claim routes the request to
 * the tenant schema. Uses a genuinely-signed bearer token — not the {@code jwt()} mock post-processor —
 * so a drift in the decoder's validator set would fail this test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TenantTest
@DisplayName("Minted HS256 token authenticates against the real decoder")
class MintedTokenAuthenticatesTest {

    // The "test" profile overrides reconcile.security.jwt.secret (app/src/test/resources/application-test.yml)
    // to this value — NOT the SecurityConfig default that tools/mint-jwt.py falls back to. This is exactly
    // the class of silent-mismatch bug Delta 5 flagged: signing against the wrong secret fails auth with no
    // indication why. The CLI's own default is still correct for its actual use (local/default profile).
    private static final String SECRET = "test-jwt-secret-must-be-at-least-32-chars";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("valid signature + future exp + tid → 200 (authenticated and tenant-routed)")
    void mintedTokenIsAccepted(TenantId tenantId) throws Exception {
        String token = mint(tenantId.value().toString(), 3600);

        mockMvc.perform(get("/api/v1/reports/runs/{runId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("expired token → 401 (exp validator enforced by the real decoder)")
    void expiredTokenIsRejected(TenantId tenantId) throws Exception {
        String expired = mint(tenantId.value().toString(), -60);

        mockMvc.perform(get("/api/v1/reports/runs/{runId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    private static String mint(String tid, long ttlSeconds) throws Exception {
        long now = Instant.now().getEpochSecond();
        String header = b64url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64url(
                ("{\"sub\":\"dev\",\"tid\":\"" + tid + "\",\"iat\":" + now + ",\"exp\":" + (now + ttlSeconds) + "}")
                        .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = b64url(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        return signingInput + "." + signature;
    }

    private static String b64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

package com.weeklycommit.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.support.AbstractPostgresIT;
import com.weeklycommit.support.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the secure (non-dev) filter chain: public health path, 401 for
 * protected paths without a token, and pass-through for a valid token. No
 * domain endpoints exist yet, so authorized requests resolve to 404 (not 401),
 * which distinguishes "auth passed" from "auth rejected".
 */
@AutoConfigureMockMvc
class SecurityConfigTest extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthPathIsPublic() throws Exception {
        // Permitted without a token; HealthController (U5) responds 200.
        mockMvc.perform(get(SecurityConfig.HEALTH_PATH))
            .andExpect(status().isOk());
    }

    @Test
    void protectedPathRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/secured-probe"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPathAcceptsValidToken() throws Exception {
        mockMvc.perform(get("/api/secured-probe")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestSecurityConfig.VALID_TOKEN))
            .andExpect(status().isNotFound());
    }
}

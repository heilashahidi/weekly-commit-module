package com.weeklycommit.rcdo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.support.AbstractPostgresIT;
import com.weeklycommit.support.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Endpoint coverage for the read-only RCDO API against the real seeded tree
 * (V3__rcdo_seed.sql) and the secure filter chain. Mirrors the auth cases in
 * SecurityConfigTest: secured-by-default, valid token passes.
 */
@AutoConfigureMockMvc
class RcdoControllerTest extends AbstractPostgresIT {

    private static final String SEEDED_RALLY_CRY = "11111111-1111-1111-1111-111111111111";
    private static final String SEEDED_SUPPORTING_OUTCOME = "44444444-4444-4444-4444-444444444441";

    @Autowired
    MockMvc mockMvc;

    private String bearer() {
        return "Bearer " + TestSecurityConfig.VALID_TOKEN;
    }

    // --- /tree (U2) ---

    @Test
    void treeReturnsNestedHierarchyForValidToken() throws Exception {
        // Covers PRD "RCDO hierarchy linking": the seeded Rally Cry root with its
        // Defining Objective children nested, down to a Supporting Outcome leaf.
        mockMvc.perform(get("/api/rcdo/tree").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nodeType").value("RALLY_CRY"))
            .andExpect(jsonPath("$[0].title").value("Become the category leader"))
            .andExpect(jsonPath("$[0].children[0].nodeType").value("DEFINING_OBJECTIVE"))
            .andExpect(jsonPath("$[0].children[0].children[0].nodeType").value("OUTCOME"))
            .andExpect(jsonPath("$[0].children[0].children[0].children[0].nodeType")
                .value("SUPPORTING_OUTCOME"));
    }

    @Test
    void treeRejectsMissingToken() throws Exception {
        // Secured-by-default: unlike /health, the RCDO API requires a JWT.
        mockMvc.perform(get("/api/rcdo/tree"))
            .andExpect(status().isUnauthorized());
    }

    // --- /nodes/{id} (U3) ---

    @Test
    void nodeReturnsDtoForExistingId() throws Exception {
        mockMvc.perform(get("/api/rcdo/nodes/" + SEEDED_SUPPORTING_OUTCOME)
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(SEEDED_SUPPORTING_OUTCOME))
            .andExpect(jsonPath("$.nodeType").value("SUPPORTING_OUTCOME"))
            .andExpect(jsonPath("$.title").value("Ship usage-based billing"));
    }

    @Test
    void nodeReturnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/rcdo/nodes/99999999-9999-9999-9999-999999999999")
                .header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isNotFound());
    }

    @Test
    void nodeRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/rcdo/nodes/" + SEEDED_RALLY_CRY))
            .andExpect(status().isUnauthorized());
    }
}

package com.weeklycommit.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weeklycommit.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class HealthControllerTest extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Test
    void healthReturnsUpAndNothingElse() throws Exception {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk())
            // strict match: exactly {"status":"UP"}, no internal detail leaked.
            .andExpect(content().json("{\"status\":\"UP\"}", true));
    }
}

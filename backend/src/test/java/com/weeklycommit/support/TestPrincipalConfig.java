package com.weeklycommit.support;

import com.weeklycommit.config.PrincipalResolver;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Overrides the auditing {@link PrincipalResolver} with a fixed principal so
 * auditing-dependent tests can assert {@code createdBy}/{@code lastModifiedBy}
 * without a live JWT. {@code @Import} this in any integration test that inspects
 * audit fields.
 */
@TestConfiguration
public class TestPrincipalConfig {

    public static final String TEST_PRINCIPAL = "test-user";

    @Bean
    @Primary
    PrincipalResolver testPrincipalResolver() {
        return () -> TEST_PRINCIPAL;
    }
}

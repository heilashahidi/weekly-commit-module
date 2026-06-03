package com.weeklycommit.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class DevProfileGuardTest {

    @Mock
    Environment environment;

    @Test
    void failsWhenDevActiveWithoutLocalMarker() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
        when(environment.getProperty("wc.local-dev")).thenReturn(null);

        DevProfileGuard guard = new DevProfileGuard(environment);

        assertThatThrownBy(guard::verifyDevProfileIsLocal)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("dev");
    }

    @Test
    void passesWhenDevActiveWithLocalMarker() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
        when(environment.getProperty("wc.local-dev")).thenReturn("true");

        DevProfileGuard guard = new DevProfileGuard(environment);

        assertThatCode(guard::verifyDevProfileIsLocal).doesNotThrowAnyException();
    }

    @Test
    void passesForNonDevProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});

        DevProfileGuard guard = new DevProfileGuard(environment);

        assertThatCode(guard::verifyDevProfileIsLocal).doesNotThrowAnyException();
    }
}

package com.weeklycommit.config;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/**
 * Supplies the current auditor to Spring Data JPA auditing. Delegates to a
 * {@link PrincipalResolver} so the principal source is swappable.
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<String> {

    private final PrincipalResolver principalResolver;

    public AuditorAwareImpl(PrincipalResolver principalResolver) {
        this.principalResolver = principalResolver;
    }

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.ofNullable(principalResolver.currentPrincipal());
    }
}

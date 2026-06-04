package com.weeklycommit.manager;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * A minimal, serialization-stable page envelope. Spring Boot 3.3 warns against
 * serializing {@code PageImpl} directly, so the team roll-up (F-U3) returns this
 * record instead: the page content plus the metadata a client needs to page.
 */
public record PageDto<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

    /** Wraps a Spring {@link Page} of already-mapped content. */
    static <T> PageDto<T> from(Page<?> page, List<T> content) {
        return new PageDto<>(
            content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}

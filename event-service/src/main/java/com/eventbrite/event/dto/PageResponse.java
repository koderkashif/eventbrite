package com.eventbrite.event.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Stable pagination shape returned by the API:
 * { "content": [...], "page": 0, "size": 20, "totalElements": 120, "totalPages": 6 }
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}

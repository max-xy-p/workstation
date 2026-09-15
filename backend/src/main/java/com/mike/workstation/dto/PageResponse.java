package com.mike.workstation.dto;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long total, boolean hasMore) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        boolean hasMore = (long) page * size + size < total;
        return new PageResponse<>(List.copyOf(items), page, size, total, hasMore);
    }
}

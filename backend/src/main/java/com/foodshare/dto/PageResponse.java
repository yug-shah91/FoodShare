package com.foodshare.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

// What every list endpoint returns: the items of ONE page plus information about the whole list.
// "hasMore" is exact, so the frontend never shows a "Load more" button that leads to an empty page.
public record PageResponse<T>(
        List<T> items,
        int page,            // this page's number (0 = first)
        int size,            // requested page size
        long totalElements,  // how many items exist in total
        int totalPages,
        boolean hasMore) {

    public static <S, T> PageResponse<T> of(Page<S> page, Function<S, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(),
                page.hasNext());
    }
}

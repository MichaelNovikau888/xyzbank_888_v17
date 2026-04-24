package com.bank.history.dto;

import lombok.Getter;
import java.util.List;

/**
 * Обёртка для постраничного вывода данных.
 * Аналог Spring Page<T>, реализованный вручную для Quarkus/JAX-RS.
 */
@Getter
public class PagedResponse<T> {

    private final List<T> content;
    private final int     page;
    private final int     size;
    private final long    totalElements;
    private final int     totalPages;
    private final boolean first;
    private final boolean last;

    public PagedResponse(List<T> content, int page, int size, long totalElements) {
        this.content       = content;
        this.page          = page;
        this.size          = size;
        this.totalElements = totalElements;
        this.totalPages    = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        this.first         = page == 0;
        this.last          = page >= this.totalPages - 1;
    }
}

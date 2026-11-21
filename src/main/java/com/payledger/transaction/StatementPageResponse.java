package com.payledger.transaction;

import java.util.List;
import org.springframework.data.domain.Page;

public record StatementPageResponse(
        List<StatementEntryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static StatementPageResponse from(Page<LedgerEntry> page) {
        return new StatementPageResponse(
                page.getContent().stream().map(StatementEntryResponse::from).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}

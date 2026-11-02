package com.visakha.snowrunner.api.dto;

import java.util.List;

/** Page envelope for the run list: summaries only, newest first. */
public record PageResponse(
    List<RunResponse> content, int page, int size, long totalElements, long totalPages) {}

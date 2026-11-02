package com.visakha.snowrunner.persistence;

import com.visakha.snowrunner.domain.RunStatus;
import com.visakha.snowrunner.domain.Source;
import java.time.Instant;

/** Optional filters for the run list, combined with AND. Null means unfiltered. */
public record RunFilter(
    Source source, String workflowId, RunStatus status, Instant from, Instant to) {}

package com.visakha.snowrunner.execution;

/**
 * A completed CALL: the database's query id for QUERY_HISTORY correlation, and how long it took.
 * The procedure's return string is not captured: 005's RUN_STEP has no column for it, so recording
 * it would mean inventing storage for it.
 */
public record CallResult(String queryId, long durationMs) {}

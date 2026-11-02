package com.visakha.snowrunner.api.dto;

/** Trigger body: the workflow to run. The idempotency key travels as a header, not here. */
public record TriggerRunRequest(String workflowId) {}

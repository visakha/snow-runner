package com.visakha.snowrunner.api;

/** An authenticated caller: an identity for audit, and a role for authorization. */
public record CallerIdentity(String id, CallerRole role) {}

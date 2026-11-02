package com.visakha.snowrunner.persistence;

/**
 * Named SQL constants for the control tables. All statements are fixed text with bound parameters;
 * the list query appends only fixed WHERE fragments, never user input, so no statement here is ever
 * string-built from request data.
 */
final class RunQueries {

  private RunQueries() {}

  static final String RUN_COLUMNS =
      "RUN_ID, WORKFLOW_ID, SOURCE, STATUS, RUN_TS, TARGET_DATABASE, CATALOG_VERSION,"
          + " APP_VERSION, TREE_VERSION, TRIGGERED_BY, IDEMPOTENCY_KEY, CURRENT_ATTEMPT_NO,"
          + " CREATED_AT, UPDATED_AT, ENDED_AT, STEPS_SUCCEEDED, STEPS_FAILED, STEPS_SKIPPED,"
          + " ERROR_MESSAGE";

  static final String STEP_COLUMNS =
      "RUN_ID, STEP_ID, ATTEMPT_NO, STEP_POSITION, PROCEDURE_NAME, STATUS, QUERY_ID,"
          + " STARTED_AT, ENDED_AT, DURATION_MS, ERROR_MESSAGE, SKIPPED_REASON";

  static final String FIND_BY_WORKFLOW_AND_KEY =
      "SELECT "
          + RUN_COLUMNS
          + " FROM RUN WHERE WORKFLOW_ID = :workflowId"
          + " AND IDEMPOTENCY_KEY = :key";

  static final String FIND_ACTIVE_BY_WORKFLOW =
      "SELECT "
          + RUN_COLUMNS
          + " FROM RUN WHERE WORKFLOW_ID = :workflowId"
          + " AND STATUS IN ('RUNNING', 'PAUSED') ORDER BY CREATED_AT DESC LIMIT 1";

  static final String LIST_RUNS_BASE = "SELECT " + RUN_COLUMNS + " FROM RUN WHERE 1 = 1";
  static final String COUNT_RUNS_BASE = "SELECT COUNT(*) FROM RUN WHERE 1 = 1";
  static final String LIST_RUNS_ORDER = " ORDER BY CREATED_AT DESC LIMIT :limit OFFSET :offset";

  static final String FIND_CURRENT_STEPS =
      "SELECT "
          + STEP_COLUMNS
          + " FROM RUN_STEP_CURRENT WHERE RUN_ID = :runId"
          + " ORDER BY STEP_POSITION";

  static final String FIND_STEPS_BY_RUN =
      "SELECT "
          + STEP_COLUMNS
          + " FROM RUN_STEP WHERE RUN_ID = :runId"
          + " ORDER BY ATTEMPT_NO, STEP_POSITION";

  static final String INSERT_RUN =
      "INSERT INTO RUN ("
          + RUN_COLUMNS
          + ") VALUES (:runId, :workflowId, :source, :status,"
          + " :runTs, :targetDatabase, :catalogVersion, :appVersion, :treeVersion, :triggeredBy,"
          + " :idempotencyKey, :currentAttemptNo, :createdAt, :updatedAt, :endedAt,"
          + " :stepsSucceeded, :stepsFailed, :stepsSkipped, :errorMessage)";

  static final String INSERT_STEP =
      "INSERT INTO RUN_STEP ("
          + STEP_COLUMNS
          + ") VALUES (:runId, :stepId, :attemptNo,"
          + " :position, :procedureName, :status, :queryId, :startedAt, :endedAt, :durationMs,"
          + " :errorMessage, :skippedReason)";

  static final String UPDATE_STEP =
      "UPDATE RUN_STEP SET STATUS = :status, QUERY_ID = :queryId, STARTED_AT = :startedAt,"
          + " ENDED_AT = :endedAt, DURATION_MS = :durationMs, ERROR_MESSAGE = :errorMessage,"
          + " SKIPPED_REASON = :skippedReason"
          + " WHERE RUN_ID = :runId AND STEP_ID = :stepId AND ATTEMPT_NO = :attemptNo";
}

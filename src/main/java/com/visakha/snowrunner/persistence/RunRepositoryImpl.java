package com.visakha.snowrunner.persistence;

import com.visakha.snowrunner.domain.Run;
import com.visakha.snowrunner.domain.RunStatus;
import com.visakha.snowrunner.domain.RunStep;
import com.visakha.snowrunner.domain.SkipReason;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.domain.StepStatus;
import com.visakha.snowrunner.domain.TargetDatabase;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Custom fragment of the one repository. Spring Data names this class after the repository
 * interface plus Impl and wires it into the repository bean itself, so it carries no component
 * annotation of its own.
 */
class RunRepositoryImpl implements RunRepositoryCustom {

  private final NamedParameterJdbcTemplate jdbc;

  RunRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Run> findByWorkflowAndKey(String workflowId, String idempotencyKey) {
    List<Run> runs =
        jdbc.query(
            RunQueries.FIND_BY_WORKFLOW_AND_KEY,
            Map.of("workflowId", workflowId, "key", idempotencyKey),
            new RunRowMapper());
    return runs.stream().findFirst();
  }

  @Override
  public Optional<Run> findActiveByWorkflow(String workflowId) {
    List<Run> runs =
        jdbc.query(
            RunQueries.FIND_ACTIVE_BY_WORKFLOW,
            Map.of("workflowId", workflowId),
            new RunRowMapper());
    return runs.stream().findFirst();
  }

  @Override
  public RunPage findRuns(RunFilter filter, int page, int size) {
    StringBuilder where = new StringBuilder();
    MapSqlParameterSource params = new MapSqlParameterSource();
    if (filter.source() != null) {
      where.append(" AND SOURCE = :source");
      params.addValue("source", filter.source().name());
    }
    if (filter.workflowId() != null) {
      where.append(" AND WORKFLOW_ID = :workflowId");
      params.addValue("workflowId", filter.workflowId());
    }
    if (filter.status() != null) {
      where.append(" AND STATUS = :status");
      params.addValue("status", filter.status().name());
    }
    if (filter.from() != null) {
      where.append(" AND CREATED_AT >= :from");
      params.addValue("from", Timestamp.from(filter.from()));
    }
    if (filter.to() != null) {
      where.append(" AND CREATED_AT <= :to");
      params.addValue("to", Timestamp.from(filter.to()));
    }
    Long total = jdbc.queryForObject(RunQueries.COUNT_RUNS_BASE + where, params, Long.class);
    params.addValue("limit", size);
    params.addValue("offset", (long) page * size);
    List<Run> content =
        jdbc.query(
            RunQueries.LIST_RUNS_BASE + where + RunQueries.LIST_RUNS_ORDER,
            params,
            new RunRowMapper());
    return new RunPage(content, total == null ? 0 : total);
  }

  @Override
  public List<RunStep> findCurrentSteps(String runId) {
    return jdbc.query(
        RunQueries.FIND_CURRENT_STEPS, Map.of("runId", runId), new RunStepRowMapper());
  }

  @Override
  public List<RunStep> findStepsByRun(String runId) {
    return jdbc.query(RunQueries.FIND_STEPS_BY_RUN, Map.of("runId", runId), new RunStepRowMapper());
  }

  @Override
  public void insertRun(Run run) {
    jdbc.update(RunQueries.INSERT_RUN, runParameters(run));
  }

  @Override
  public void insertStep(RunStep step) {
    jdbc.update(RunQueries.INSERT_STEP, stepParameters(step));
  }

  @Override
  public void updateStep(RunStep step) {
    jdbc.update(RunQueries.UPDATE_STEP, stepParameters(step));
  }

  private static MapSqlParameterSource runParameters(Run run) {
    return new MapSqlParameterSource()
        .addValue("runId", run.runId())
        .addValue("workflowId", run.workflowId())
        .addValue("source", run.source().name())
        .addValue("status", run.status().name())
        .addValue("runTs", toTimestamp(run.runTs()))
        .addValue("targetDatabase", run.targetDatabase().name())
        .addValue("catalogVersion", run.catalogVersion())
        .addValue("appVersion", run.appVersion())
        .addValue("treeVersion", run.treeVersion())
        .addValue("triggeredBy", run.triggeredBy())
        .addValue("idempotencyKey", run.idempotencyKey())
        .addValue("currentAttemptNo", run.currentAttemptNo())
        .addValue("createdAt", toTimestamp(run.createdAt()))
        .addValue("updatedAt", toTimestamp(run.updatedAt()))
        .addValue("endedAt", toTimestamp(run.endedAt()))
        .addValue("stepsSucceeded", run.stepsSucceeded())
        .addValue("stepsFailed", run.stepsFailed())
        .addValue("stepsSkipped", run.stepsSkipped())
        .addValue("errorMessage", run.errorMessage());
  }

  private static MapSqlParameterSource stepParameters(RunStep step) {
    return new MapSqlParameterSource()
        .addValue("runId", step.runId())
        .addValue("stepId", step.stepId())
        .addValue("attemptNo", step.attemptNo())
        .addValue("position", step.position())
        .addValue("procedureName", step.procedureName())
        .addValue("status", step.status().name())
        .addValue("queryId", step.queryId())
        .addValue("startedAt", toTimestamp(step.startedAt()))
        .addValue("endedAt", toTimestamp(step.endedAt()))
        .addValue("durationMs", step.durationMs())
        .addValue("errorMessage", step.errorMessage())
        .addValue(
            "skippedReason", step.skippedReason() == null ? null : step.skippedReason().name());
  }

  private static Timestamp toTimestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  // NUMBER columns arrive as BigDecimal from H2 and from Snowflake alike;
  // a direct cast to Long or Integer fails on both, so convert via Number.
  private static Integer toInteger(Object value) {
    return value == null ? null : ((Number) value).intValue();
  }

  private static Long toLong(Object value) {
    return value == null ? null : ((Number) value).longValue();
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static final class RunRowMapper implements RowMapper<Run> {
    @Override
    public Run mapRow(ResultSet rs, int rowNum) throws SQLException {
      return Run.reconstitute(
          rs.getString("RUN_ID"),
          rs.getString("WORKFLOW_ID"),
          Source.valueOf(rs.getString("SOURCE")),
          RunStatus.valueOf(rs.getString("STATUS")),
          toInstant(rs.getTimestamp("RUN_TS")),
          TargetDatabase.valueOf(rs.getString("TARGET_DATABASE")),
          rs.getString("CATALOG_VERSION"),
          rs.getString("APP_VERSION"),
          rs.getString("TREE_VERSION"),
          rs.getString("TRIGGERED_BY"),
          rs.getString("IDEMPOTENCY_KEY"),
          rs.getInt("CURRENT_ATTEMPT_NO"),
          toInstant(rs.getTimestamp("CREATED_AT")),
          toInstant(rs.getTimestamp("UPDATED_AT")),
          toInstant(rs.getTimestamp("ENDED_AT")),
          toInteger(rs.getObject("STEPS_SUCCEEDED")),
          toInteger(rs.getObject("STEPS_FAILED")),
          toInteger(rs.getObject("STEPS_SKIPPED")),
          rs.getString("ERROR_MESSAGE"));
    }
  }

  private static final class RunStepRowMapper implements RowMapper<RunStep> {
    @Override
    public RunStep mapRow(ResultSet rs, int rowNum) throws SQLException {
      String skipped = rs.getString("SKIPPED_REASON");
      return new RunStep(
          rs.getString("RUN_ID"),
          rs.getString("STEP_ID"),
          rs.getInt("ATTEMPT_NO"),
          rs.getInt("STEP_POSITION"),
          rs.getString("PROCEDURE_NAME"),
          StepStatus.valueOf(rs.getString("STATUS")),
          rs.getString("QUERY_ID"),
          toInstant(rs.getTimestamp("STARTED_AT")),
          toInstant(rs.getTimestamp("ENDED_AT")),
          toLong(rs.getObject("DURATION_MS")),
          rs.getString("ERROR_MESSAGE"),
          skipped == null ? null : SkipReason.valueOf(skipped));
    }
  }
}

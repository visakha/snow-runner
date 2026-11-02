package com.visakha.snowrunner.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * The RUN row (005 §2). Status is a stored value, updated at transitions — never computed by
 * counting steps in application code. Step rows are loaded separately and held transiently because
 * RUN_STEP's key (run, step, attempt) cannot be a mapped collection: one step has several rows
 * across attempts.
 */
@Table("RUN")
public class Run {

  @Id
  @Column("RUN_ID")
  private String runId;

  @Column("WORKFLOW_ID")
  private String workflowId;

  @Column("SOURCE")
  private Source source;

  @Column("STATUS")
  private RunStatus status;

  @Column("RUN_TS")
  private Instant runTs;

  @Column("TARGET_DATABASE")
  private TargetDatabase targetDatabase;

  @Column("CATALOG_VERSION")
  private String catalogVersion;

  @Column("APP_VERSION")
  private String appVersion;

  @Column("TREE_VERSION")
  private String treeVersion;

  @Column("TRIGGERED_BY")
  private String triggeredBy;

  @Column("IDEMPOTENCY_KEY")
  private String idempotencyKey;

  @Column("CURRENT_ATTEMPT_NO")
  private int currentAttemptNo;

  @Column("CREATED_AT")
  private Instant createdAt;

  @Column("UPDATED_AT")
  private Instant updatedAt;

  @Column("ENDED_AT")
  private Instant endedAt;

  @Column("STEPS_SUCCEEDED")
  private Integer stepsSucceeded;

  @Column("STEPS_FAILED")
  private Integer stepsFailed;

  @Column("STEPS_SKIPPED")
  private Integer stepsSkipped;

  @Column("ERROR_MESSAGE")
  private String errorMessage;

  @Transient private List<RunStep> currentSteps = List.of();

  /** Creates a new run in RUNNING state for its first attempt. */
  public static Run create(
      RunId runId,
      String workflowId,
      Source source,
      RunTimestamp runTs,
      TargetDatabase targetDatabase,
      String catalogVersion,
      String appVersion,
      String treeVersion,
      String triggeredBy,
      String idempotencyKey,
      Instant now) {
    Run run = new Run();
    run.runId = runId.value();
    run.workflowId = workflowId;
    run.source = source;
    run.status = RunStatus.RUNNING;
    run.runTs = runTs.value();
    run.targetDatabase = targetDatabase;
    run.catalogVersion = catalogVersion;
    run.appVersion = appVersion;
    run.treeVersion = treeVersion;
    run.triggeredBy = triggeredBy;
    run.idempotencyKey = idempotencyKey;
    run.currentAttemptNo = 1;
    run.createdAt = now;
    run.updatedAt = now;
    return run;
  }

  /** Rebuilds a Run from a database row, for the repository's row mapper. */
  public static Run reconstitute(
      String runId,
      String workflowId,
      Source source,
      RunStatus status,
      Instant runTs,
      TargetDatabase targetDatabase,
      String catalogVersion,
      String appVersion,
      String treeVersion,
      String triggeredBy,
      String idempotencyKey,
      int currentAttemptNo,
      Instant createdAt,
      Instant updatedAt,
      Instant endedAt,
      Integer stepsSucceeded,
      Integer stepsFailed,
      Integer stepsSkipped,
      String errorMessage) {
    Run run = new Run();
    run.runId = runId;
    run.workflowId = workflowId;
    run.source = source;
    run.status = status;
    run.runTs = runTs;
    run.targetDatabase = targetDatabase;
    run.catalogVersion = catalogVersion;
    run.appVersion = appVersion;
    run.treeVersion = treeVersion;
    run.triggeredBy = triggeredBy;
    run.idempotencyKey = idempotencyKey;
    run.currentAttemptNo = currentAttemptNo;
    run.createdAt = createdAt;
    run.updatedAt = updatedAt;
    run.endedAt = endedAt;
    run.stepsSucceeded = stepsSucceeded;
    run.stepsFailed = stepsFailed;
    run.stepsSkipped = stepsSkipped;
    run.errorMessage = errorMessage;
    return run;
  }

  public String runId() {
    return runId;
  }

  public String workflowId() {
    return workflowId;
  }

  public Source source() {
    return source;
  }

  public RunStatus status() {
    return status;
  }

  public void status(RunStatus status) {
    this.status = status;
  }

  public Instant runTs() {
    return runTs;
  }

  public TargetDatabase targetDatabase() {
    return targetDatabase;
  }

  public String catalogVersion() {
    return catalogVersion;
  }

  public String appVersion() {
    return appVersion;
  }

  public String treeVersion() {
    return treeVersion;
  }

  public String triggeredBy() {
    return triggeredBy;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public int currentAttemptNo() {
    return currentAttemptNo;
  }

  public void currentAttemptNo(int currentAttemptNo) {
    this.currentAttemptNo = currentAttemptNo;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public void updatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant endedAt() {
    return endedAt;
  }

  public void endedAt(Instant endedAt) {
    this.endedAt = endedAt;
  }

  public Integer stepsSucceeded() {
    return stepsSucceeded;
  }

  public void stepsSucceeded(Integer stepsSucceeded) {
    this.stepsSucceeded = stepsSucceeded;
  }

  public Integer stepsFailed() {
    return stepsFailed;
  }

  public void stepsFailed(Integer stepsFailed) {
    this.stepsFailed = stepsFailed;
  }

  public Integer stepsSkipped() {
    return stepsSkipped;
  }

  public void stepsSkipped(Integer stepsSkipped) {
    this.stepsSkipped = stepsSkipped;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public void errorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public List<RunStep> currentSteps() {
    return currentSteps;
  }

  public void currentSteps(List<RunStep> currentSteps) {
    this.currentSteps = currentSteps;
  }
}

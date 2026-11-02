package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.StepCatalog;
import com.visakha.snowrunner.catalog.WorkflowDefinition;
import com.visakha.snowrunner.domain.Run;
import com.visakha.snowrunner.domain.RunStep;
import com.visakha.snowrunner.persistence.RunFilter;
import com.visakha.snowrunner.persistence.RunPage;
import com.visakha.snowrunner.persistence.RunRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Read side: lists, detail, attempt history, and the catalog as the application sees it. */
@Component
public class RunQueryService {

  private final StepCatalog catalog;
  private final RunRepository repository;

  /** Creates the service. */
  public RunQueryService(StepCatalog catalog, RunRepository repository) {
    this.catalog = catalog;
    this.repository = repository;
  }

  /** Returns one page of run summaries, newest first. Size is clamped, never rejected. */
  public RunPage listRuns(RunFilter filter, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? 50 : Math.min(size, 200);
    return repository.findRuns(filter, safePage, safeSize);
  }

  /** Returns a run with its current steps attached. */
  public Run getRun(String runId) {
    Run run = repository.findById(runId).orElseThrow(() -> ServiceException.runNotFound(runId));
    run.currentSteps(repository.findCurrentSteps(runId));
    return run;
  }

  /** Returns attempt history: every step row grouped by attempt, attempts ascending. */
  public List<AttemptHistory> getAttempts(String runId) {
    repository.findById(runId).orElseThrow(() -> ServiceException.runNotFound(runId));
    List<RunStep> rows = repository.findStepsByRun(runId);
    Map<Integer, List<RunStep>> grouped = new LinkedHashMap<>();
    for (RunStep row : rows) {
      grouped.computeIfAbsent(row.attemptNo(), key -> new ArrayList<>()).add(row);
    }
    return grouped.entrySet().stream()
        .map(entry -> new AttemptHistory(entry.getKey(), entry.getValue()))
        .toList();
  }

  /** Returns the catalog's workflows. */
  public List<WorkflowDefinition> listWorkflows() {
    return catalog.workflows();
  }

  /** Returns the catalog version. */
  public String catalogVersion() {
    return catalog.catalogVersion();
  }

  /** One attempt's steps. */
  public record AttemptHistory(int attemptNo, List<RunStep> steps) {}
}

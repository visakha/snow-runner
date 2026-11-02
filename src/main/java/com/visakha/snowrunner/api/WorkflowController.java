package com.visakha.snowrunner.api;

import com.visakha.snowrunner.api.dto.DryRunResponse;
import com.visakha.snowrunner.api.dto.WorkflowResponse;
import com.visakha.snowrunner.execution.RunExecutor;
import com.visakha.snowrunner.execution.RunQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Workflow endpoints: the catalog listing and the scheduler-only dry-run. */
@RestController
@RequestMapping("/api/v1")
public class WorkflowController {

  private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);

  private final RunExecutor executor;
  private final RunQueryService queries;

  /** Creates the controller. */
  public WorkflowController(RunExecutor executor, RunQueryService queries) {
    this.executor = executor;
    this.queries = queries;
  }

  /** Lists the catalog as the application sees it. */
  @GetMapping("/workflows")
  public WorkflowResponse listWorkflows() {
    return WorkflowResponse.from(queries.catalogVersion(), queries.listWorkflows());
  }

  /** Lists a workflow's ordered steps with resolved names. No run, no CALL. */
  @PostMapping("/workflows/{workflowId}/dry-run")
  public ResponseEntity<DryRunResponse> dryRun(
      @PathVariable String workflowId, HttpServletRequest request) {
    CallerIdentity caller =
        (CallerIdentity) request.getAttribute(CallerIdentityFilter.CALLER_ATTRIBUTE);
    log.info("control dry-run workflow={} by={}", workflowId, caller.id());
    return ResponseEntity.ok(DryRunResponse.from(executor.dryRun(workflowId)));
  }
}

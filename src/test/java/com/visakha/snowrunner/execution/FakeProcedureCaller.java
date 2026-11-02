package com.visakha.snowrunner.execution;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test double for ProcedureCaller: records calls, succeeds instantly, and can be told to fail one
 * step or to block a call until a test releases it. Hand-written fake, not a mock framework: its
 * behavior is explicit state a test sets and reads.
 */
public class FakeProcedureCaller implements ProcedureCaller {

  private final List<StepCall> calls = new CopyOnWriteArrayList<>();
  private volatile String failOnStepId;
  private volatile String failureMessage;
  private volatile CountDownLatch blockLatch;

  /** Returns the calls recorded so far, in order. */
  public List<StepCall> calls() {
    return calls;
  }

  /** Makes the named step fail with the given message on its next call. */
  public void failOn(String stepId, String message) {
    this.failOnStepId = stepId;
    this.failureMessage = message;
  }

  /** Blocks the next CALL until the latch is released (pause/cancel tests). */
  public void blockOn(CountDownLatch latch) {
    this.blockLatch = latch;
  }

  /** Clears a programmed failure without erasing the recorded calls. */
  public void clearFailure() {
    failOnStepId = null;
    failureMessage = null;
  }

  /** Resets all programmed behavior and recorded calls. */
  public void reset() {
    calls.clear();
    failOnStepId = null;
    failureMessage = null;
    blockLatch = null;
  }

  @Override
  public CallResult call(StepCall call) {
    calls.add(call);
    CountDownLatch latch = blockLatch;
    if (latch != null) {
      try {
        if (!latch.await(30, TimeUnit.SECONDS)) {
          throw new ProcedureCallException("Test latch timed out");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new ProcedureCallException("Test latch interrupted");
      }
    }
    if (call.stepId().equals(failOnStepId)) {
      throw new ProcedureCallException(failureMessage);
    }
    return new CallResult("test-query-" + calls.size(), 5);
  }
}

package com.visakha.snowrunner.execution;

import com.visakha.snowrunner.catalog.StepDefinition;
import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.Source;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The code registry binding steps to handlers by constructor injection — no class name appears in
 * YAML, so no reflection is ever needed to find a handler. A handler attaches to a step when the
 * source matches, the step runs the handler's phase, and the handler's dimensions overlap the
 * step's touches.
 */
@Component
public class PhaseHandlerRegistry {

  private final List<PhaseHandler> handlers;
  private final List<LifecycleHook> hooks;

  /** Collects every handler and hook bean Spring found. */
  public PhaseHandlerRegistry(List<PhaseHandler> handlers, List<LifecycleHook> hooks) {
    this.handlers = List.copyOf(handlers);
    this.hooks = List.copyOf(hooks);
  }

  /** Returns the handlers attached to this step, in no guaranteed order. */
  public List<PhaseHandler> handlersFor(StepDefinition step, Source source) {
    Set<Dimension> touches = step.touches();
    return handlers.stream()
        .filter(handler -> handler.source() == source)
        .filter(handler -> step.phases().contains(handler.phase()))
        .filter(handler -> !intersect(handler.dimensions(), touches).isEmpty())
        .toList();
  }

  /** Returns the hooks for one lifecycle operation of one source. */
  public List<LifecycleHook> hooksFor(Source source, LifecycleRole role) {
    return hooks.stream().filter(hook -> hook.source() == source && hook.role() == role).toList();
  }

  private static <T> Set<T> intersect(Set<T> left, Set<T> right) {
    Set<T> result = new HashSet<>(left);
    result.retainAll(right);
    return result;
  }
}

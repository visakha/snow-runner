package com.visakha.snowrunner.catalog;

import com.visakha.snowrunner.domain.Dimension;
import com.visakha.snowrunner.domain.ProcedureName;
import com.visakha.snowrunner.domain.RerunSafety;
import com.visakha.snowrunner.domain.Source;
import com.visakha.snowrunner.domain.StepId;
import com.visakha.snowrunner.domain.TargetDatabase;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/**
 * The catalog loaded from steps-catalog.yml. Parsing goes through SnakeYAML into a generic document
 * tree exactly once, at load, and is immediately converted into typed, immutable records with
 * fail-fast validation; no other code ever sees the tree, so a typo becomes a startup error with a
 * location, never a runtime surprise.
 *
 * <p>Two deliberate deviations from 003, both forced by the actual catalog file: step position
 * comes from list order (the file carries no position field), and the parameters block is a map of
 * name to binding rather than a list of name/value pairs.
 */
public final class YamlStepCatalog implements StepCatalog {

  private static final int DEFAULT_TIMEOUT_MINUTES = 30;
  private static final String RUN_TIMESTAMP_BINDING = "$RUN_TIMESTAMP";
  private static final String SOURCE_BINDING = "$SOURCE";

  private final String catalogVersion;
  private final TargetDatabase targetDatabase;
  private final List<WorkflowDefinition> workflows;
  private final Map<String, WorkflowDefinition> byId;

  private YamlStepCatalog(
      String catalogVersion, TargetDatabase targetDatabase, List<WorkflowDefinition> workflows) {
    this.catalogVersion = catalogVersion;
    this.targetDatabase = targetDatabase;
    this.workflows = List.copyOf(workflows);
    Map<String, WorkflowDefinition> index = new HashMap<>();
    for (WorkflowDefinition workflow : workflows) {
      index.put(workflow.id(), workflow);
    }
    this.byId = Map.copyOf(index);
  }

  /**
   * Loads and validates the catalog document. Any problem rejects the whole document: no partial
   * catalog ever executes.
   */
  public static YamlStepCatalog load(InputStream input, String location) {
    Object parsed;
    try {
      parsed = new Yaml().load(input);
    } catch (Exception e) {
      throw new CatalogException(location + ": malformed YAML: " + e.getMessage(), e);
    }
    Map<String, Object> document = asMap(parsed, location + ": document must be a mapping");
    String version = requiredString(document, "catalogVersion", location);
    TargetDatabase target = parseTarget(requiredString(document, "targetDatabase", location));
    Map<String, String> schemas = selectedSchemas(document, target, location);
    Defaults defaults = parseDefaults(document, location);
    List<WorkflowDefinition> workflows =
        parseWorkflows(document, target, schemas, defaults, location);
    return new YamlStepCatalog(version, target, workflows);
  }

  @Override
  public String catalogVersion() {
    return catalogVersion;
  }

  @Override
  public TargetDatabase targetDatabase() {
    return targetDatabase;
  }

  @Override
  public List<WorkflowDefinition> workflows() {
    return workflows;
  }

  @Override
  public Optional<WorkflowDefinition> workflow(String workflowId) {
    return Optional.ofNullable(byId.get(workflowId));
  }

  private static TargetDatabase parseTarget(String value) {
    return switch (value) {
      case "snowflake" -> TargetDatabase.SNOWFLAKE;
      case "oracle" -> TargetDatabase.ORACLE;
      default -> throw new CatalogException("catalog: unknown targetDatabase '" + value + "'");
    };
  }

  private static Map<String, String> selectedSchemas(
      Map<String, Object> document, TargetDatabase target, String location) {
    Map<String, Object> schemas =
        asMap(document.get("schemas"), location + ": missing 'schemas' mapping");
    String key = target == TargetDatabase.SNOWFLAKE ? "snowflake" : "oracle";
    Map<String, Object> selected =
        asMap(schemas.get(key), location + ": missing schemas for '" + key + "'");
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, Object> entry : selected.entrySet()) {
      result.put(entry.getKey(), requiredString(selected, entry.getKey(), location));
    }
    return result;
  }

  private record Defaults(int timeoutMinutes, List<ParameterBinding> parameters) {}

  private static Defaults parseDefaults(Map<String, Object> document, String location) {
    Object raw = document.get("defaults");
    if (raw == null) {
      return new Defaults(DEFAULT_TIMEOUT_MINUTES, List.of());
    }
    Map<String, Object> defaults = asMap(raw, location + ": 'defaults' must be a mapping");
    int timeout = DEFAULT_TIMEOUT_MINUTES;
    if (defaults.containsKey("timeoutMinutes")) {
      timeout = requiredInt(defaults, "timeoutMinutes", location);
      if (timeout <= 0) {
        throw new CatalogException(location + ": timeoutMinutes must be positive");
      }
    }
    List<ParameterBinding> parameters = List.of();
    if (defaults.containsKey("parameters")) {
      parameters =
          parseParameters(
              asMap(defaults.get("parameters"), location + ": 'parameters' must be a mapping"),
              location);
    }
    return new Defaults(timeout, parameters);
  }

  private static List<ParameterBinding> parseParameters(Map<String, Object> raw, String location) {
    List<ParameterBinding> parameters = new ArrayList<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      String binding = requiredString(raw, entry.getKey(), location);
      if (binding.startsWith("$")
          && !binding.equals(RUN_TIMESTAMP_BINDING)
          && !binding.equals(SOURCE_BINDING)) {
        throw new CatalogException(location + ": unknown parameter binding '" + binding + "'");
      }
      parameters.add(new ParameterBinding(entry.getKey(), binding));
    }
    return parameters;
  }

  private static List<WorkflowDefinition> parseWorkflows(
      Map<String, Object> document,
      TargetDatabase target,
      Map<String, String> schemas,
      Defaults defaults,
      String location) {
    Object raw = document.get("workflows");
    List<Object> entries = asList(raw, location + ": missing 'workflows' list");
    if (entries.isEmpty()) {
      throw new CatalogException(location + ": 'workflows' must not be empty");
    }
    List<WorkflowDefinition> workflows = new ArrayList<>();
    Set<String> seenWorkflowIds = new HashSet<>();
    for (Object entry : entries) {
      Map<String, Object> workflowMap =
          asMap(entry, location + ": each workflow must be a mapping");
      String id = requiredString(workflowMap, "id", location);
      if (!seenWorkflowIds.add(id)) {
        throw new CatalogException(location + ": duplicate workflow id '" + id + "'");
      }
      String workflowLocation = location + " workflow '" + id + "'";
      Source source =
          parseSource(requiredString(workflowMap, "source", workflowLocation), workflowLocation);
      String schema = schemas.get(source.name());
      if (schema == null) {
        throw new CatalogException(workflowLocation + ": source has no schema mapping");
      }
      List<Object> stepEntries =
          asList(workflowMap.get("steps"), workflowLocation + ": missing 'steps' list");
      List<StepDefinition> steps = new ArrayList<>();
      Set<String> seenStepIds = new HashSet<>();
      int position = 0;
      for (Object stepEntry : stepEntries) {
        position++;
        Map<String, Object> stepMap =
            asMap(stepEntry, workflowLocation + ": each step must be a mapping");
        String stepId = requiredString(stepMap, "id", workflowLocation);
        String stepLocation = workflowLocation + " step '" + stepId + "'";
        if (!seenStepIds.add(stepId)) {
          throw new CatalogException(stepLocation + ": duplicate step id");
        }
        if (!stepId.matches("[A-Z][A-Z0-9_]*")) {
          throw new CatalogException(
              stepLocation + ": step id must be uppercase letters, digits, underscores");
        }
        String procedure = requiredString(stepMap, "procedure", stepLocation);
        String oracleProcedure = requiredString(stepMap, "oracleProcedure", stepLocation);
        String chosen = target == TargetDatabase.SNOWFLAKE ? procedure : oracleProcedure;
        boolean enabled = requiredBoolean(stepMap, "enabled", stepLocation);
        List<Phase> phases = parsePhases(stepMap, stepLocation);
        Set<Dimension> touches = parseTouches(stepMap, stepLocation);
        RerunSafety rerunSafety = parseRerunSafety(stepMap, stepLocation);
        int timeout = defaults.timeoutMinutes();
        if (stepMap.containsKey("timeoutMinutes")) {
          timeout = requiredInt(stepMap, "timeoutMinutes", stepLocation);
          if (timeout <= 0) {
            throw new CatalogException(stepLocation + ": timeoutMinutes must be positive");
          }
        }
        Map<String, ParameterBinding> parameters = new LinkedHashMap<>();
        for (ParameterBinding binding : defaults.parameters()) {
          parameters.put(binding.name(), binding);
        }
        if (stepMap.containsKey("parameters")) {
          for (ParameterBinding binding :
              parseParameters(
                  asMap(
                      stepMap.get("parameters"), stepLocation + ": 'parameters' must be a mapping"),
                  stepLocation)) {
            parameters.put(binding.name(), binding);
          }
        }
        steps.add(
            new StepDefinition(
                new StepId(stepId),
                position,
                new ProcedureName(schema, chosen),
                enabled,
                List.copyOf(phases),
                Set.copyOf(touches),
                rerunSafety,
                timeout,
                List.copyOf(parameters.values())));
      }
      workflows.add(new WorkflowDefinition(id, source, List.copyOf(steps)));
    }
    return workflows;
  }

  private static Source parseSource(String value, String location) {
    try {
      return Source.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new CatalogException(location + ": unknown source '" + value + "'", e);
    }
  }

  private static List<Phase> parsePhases(Map<String, Object> stepMap, String location) {
    List<Object> raw = asList(stepMap.get("phases"), location + ": missing 'phases' list");
    List<Phase> phases = new ArrayList<>();
    for (Object value : raw) {
      if (!(value instanceof String phase)) {
        throw new CatalogException(location + ": phases must be strings");
      }
      phases.add(Phase.fromYaml(phase, location));
    }
    return phases;
  }

  private static Set<Dimension> parseTouches(Map<String, Object> stepMap, String location) {
    List<Object> raw = asList(stepMap.get("touches"), location + ": missing 'touches' list");
    Set<Dimension> touches = new HashSet<>();
    for (Object value : raw) {
      if (!(value instanceof String name)) {
        throw new CatalogException(location + ": touches must be strings");
      }
      try {
        touches.add(Dimension.valueOf(name));
      } catch (IllegalArgumentException e) {
        throw new CatalogException(location + ": unknown dimension '" + name + "'", e);
      }
    }
    return touches;
  }

  private static RerunSafety parseRerunSafety(Map<String, Object> stepMap, String location) {
    String value = requiredString(stepMap, "rerunSafety", location);
    try {
      return RerunSafety.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new CatalogException(location + ": unknown rerunSafety '" + value + "'", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value, String message) {
    if (value instanceof Map<?, ?> map) {
      return (Map<String, Object>) map;
    }
    throw new CatalogException(message);
  }

  @SuppressWarnings("unchecked")
  private static List<Object> asList(Object value, String message) {
    if (value instanceof List<?> list) {
      return (List<Object>) list;
    }
    throw new CatalogException(message);
  }

  private static String requiredString(Map<String, Object> map, String key, String location) {
    Object value = map.get(key);
    if (value instanceof String text && !text.isBlank()) {
      return text;
    }
    throw new CatalogException(location + ": missing or blank '" + key + "'");
  }

  private static boolean requiredBoolean(Map<String, Object> map, String key, String location) {
    Object value = map.get(key);
    if (value instanceof Boolean flag) {
      return flag;
    }
    throw new CatalogException(location + ": missing or non-boolean '" + key + "'");
  }

  private static int requiredInt(Map<String, Object> map, String key, String location) {
    Object value = map.get(key);
    if (value instanceof Number number) {
      return number.intValue();
    }
    throw new CatalogException(location + ": missing or non-numeric '" + key + "'");
  }
}

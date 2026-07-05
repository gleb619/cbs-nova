# T24 — MermaidDiagramGenerator

## Goal

Replace the placeholder Mermaid string in `DevDslRuntime.explain()` with a real flowchart generated from
`ProcessDescriptor` or `TransactionDescriptor` metadata.

## Acceptance Criteria

- New class `MermaidDiagramGenerator` in `cbs.nova.dsl` package (`dsl` module)
- `forProcess(ProcessDescriptor)` returns a Mermaid `graph TD` string showing:
  - `Start([Start]) --> Execute[<name> execute]`
  - `Execute --> |success| End([End])`
  - If `hasCompensation`: `Execute --> |failure| Compensate[Compensate]` + `Compensate --> End`
  - If `helperRefs` non-empty: node for each helper ref connected from Execute
- `forTransaction(TransactionDescriptor)` similar but for activity shape
- `DevDslRuntime.explain()` — call `MermaidDiagramGenerator.forProcess()` or `forTransaction()`
  depending on which entity is found in GlobalManager; fall back to placeholder for helpers
- Unit test: `MermaidDiagramGeneratorTest` in `dsl` module verifying process with/without compensation,
  and transaction

## Files to Create / Modify

- **Create**: `backend/dsl/src/main/java/cbs/nova/dsl/MermaidDiagramGenerator.java`
- **Create**: `backend/dsl/src/test/java/cbs/nova/dsl/MermaidDiagramGeneratorTest.java`
- **Modify**: `backend/starter/src/main/java/cbs/nova/starter/DevDslRuntime.java` — call generator

## Implementation Notes

```java
public final class MermaidDiagramGenerator {
  public static @NonNull String forProcess(@NonNull ProcessDescriptor d) {
    var sb = new StringBuilder("graph TD\n");
    sb.append("  Start([Start]) --> Execute[").append(d.name()).append("]\n");
    sb.append("  Execute --> |success| End([End])\n");
    if (d.hasCompensation()) {
      sb.append("  Execute --> |failure| Compensate[Compensate]\n");
      sb.append("  Compensate --> End\n");
    }
    for (var ref : d.helperRefs()) {
      sb.append("  Execute --> Helper_").append(ref).append("[").append(ref).append("]\n");
    }
    return sb.toString().stripTrailing();
  }

  public static @NonNull String forTransaction(@NonNull TransactionDescriptor d) {
    return "graph TD\n"
        + "  Start([Start]) --> Activity[" + d.name() + "]\n"
        + "  Activity --> |success| End([End])\n"
        + (d.hasCompensation()
            ? "  Activity --> |failure| Compensate[Compensate]\n  Compensate --> End"
            : "  Activity --> |failure| Fail([Fail])");
  }
}
```

## DevDslRuntime change

In `explain()`, replace the placeholder mermaid line with:
```java
GlobalManager gm = GlobalManager.getInstance();
String mermaid;
if (gm.hasProcess(name)) {
  // need ProcessDescriptor — get from registry
  mermaid = "graph TD\n  Start([Start]) --> Execute[" + name + "]\n  Execute --> End([End])";
} else {
  mermaid = "graph TD\n  Start([Start]) --> " + name + " --> End([End])";
}
```

Actually — GlobalManager doesn't expose descriptors, only run methods. So DevDslRuntime should build
a minimal diagram from just the name and whether process/transaction/helper:

```java
String mermaid;
if (gm.hasProcess(name)) {
  mermaid = "graph TD\n  Start([Start]) --> Execute[" + name + "]\n  Execute --> |success| End([End])";
} else if (gm.hasTransaction(name)) {
  mermaid = "graph TD\n  Start([Start]) --> Activity[" + name + "]\n  Activity --> |success| End([End])";
} else {
  mermaid = "graph TD\n  Start([Start]) --> Helper[" + name + "]\n  Helper --> End([End])";
}
```

And `MermaidDiagramGenerator` is used in `dsl-codegen` (from descriptors during compile-time generation).
Add `MermaidDiagramGenerator` to `dsl` module and use it in both `DevDslRuntime` and `DslCompiler` context report.

## Build & Test

From `backend/`:
```
./gradlew spotlessApply
./gradlew :dsl:build :dsl:test :starter:build :starter:test
```

## Constraints

- Java 25, 2-space indent, Spotless must pass
- `MermaidDiagramGenerator` goes in `dsl` module (not `dsl-codegen` or `starter`)
- Commit: `feat(T24): add MermaidDiagramGenerator for explain mode flowcharts`

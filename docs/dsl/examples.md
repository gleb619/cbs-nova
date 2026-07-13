# How to work with DSL examples

The `backend/dsl-examples` module contains real-world DSL definitions. They are written as
[JEP-512 compact source files](https://openjdk.org/jeps/512) and compiled into Temporal
workflows/activities at build time.

## Where the examples live

```
backend/dsl-examples/src/
├── BatchProcessingDsl.java
├── ExceptionProbeDsl.java
├── InvoiceGenerationDsl.java
├── LongWorkSimulationDsl.java
├── NestedCompensationDsl.java
├── OrderSagaDsl.java
├── SampleProcessDsl.java
├── SimpleGreetingDsl.java
├── SimpleOrderDsl.java
├── SimpleValidationDsl.java
└── ...
```

Every file exposes a `List<DslObject> define()` method built with the fluent DSL API.

## Building the examples

The `compileDsl` Gradle task scans `dsl-examples/src`, loads the definitions, validates them,
and generates Temporal classes under `dsl-examples/build/generated`.

```bash
cd backend
./gradlew :dsl-examples:compileDsl
```

After a successful run you will find generated classes such as:

```
backend/dsl-examples/build/generated/cbs/nova/dsl/generated/batchprocessing/v1/
├── BatchProcessingProcessWorkflow.java
└── BatchProcessingProcessDefinition.java
```

The generated package is `cbs.nova.dsl.generated.<name>.<version>` where `<name>` is the
process/transaction name lower-cased and `<version>` is the version declared in the DSL
(default `v1`).

## Running the integration test

`backend/starter-example` contains a Testcontainers-based integration test that starts a real
Temporal server plus PostgreSQL, registers the generated `BatchProcessing` worker, and runs
the workflow end-to-end.

```bash
cd backend
./gradlew :starter-example:test \
  --tests cbs.nova.dsl.example.integration.BatchProcessingDslIntegrationTest
```

The test does the following:

1. Starts `postgres:15` and `temporalio/auto-setup:1.25.2` containers on a shared Docker
   network.
2. Loads `dsl-examples/src` with `DefinitionLoader` into a fresh `GlobalManager`.
3. Points a Temporal `WorkflowClient` at the exposed gRPC port.
4. Registers `BatchProcessingProcessDefinition` on the `BatchProcessing-queue` task queue.
5. Executes the workflow with a `BatchIn` record and asserts the returned `BatchOut`.

## How input/output types are handled

When a process declares `.input(BatchIn.class)` and `.output(BatchOut.class)`, the DSL
generator produces a strongly-typed Temporal workflow interface:

```java
@WorkflowInterface
public interface BatchProcessingProcessWorkflow {
  @WorkflowMethod
  BatchOut run(BatchIn input);
}
```

Using concrete types lets Temporal serialize/deserialize the arguments and results correctly.
Without them, Temporal would deserialize JSON payloads as `LinkedHashMap` and the DSL body
would fail with a `ClassCastException`.

## Adding a new example

1. Create a compact source file in `backend/dsl-examples/src/`.
2. Use `Dsl.process(...)`, `Dsl.transaction(...)`, or `Dsl.function(...)` inside `define()`.
3. Declare `.input(...)` / `.output(...)` when the workflow needs typed payloads.
4. Run `./gradlew :dsl-examples:compileDsl` to validate generation.
5. Optionally add an integration test in `backend/starter-example/src/test/java` that loads
   the new DSL, starts a Temporal worker, and executes the generated workflow.

## Tips

- DSL source files must not contain a `package` declaration or `public` modifier. They rely on
  the JEP-512 compact-source convention (`void main() {}` is required by the current loader).
- If `DefinitionLoader` reports compilation errors, fix the DSL source first; generated code
  will not be produced for files that fail to compile.
- The integration test resets `GlobalManager` before each run so tests do not share state
  between executions.

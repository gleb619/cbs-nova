# Compile-Time Code Generation

This page describes how the DSL compiler turns DSL source files into Temporal classes, how generated classes are named
and versioned, the Gradle module setup, and what the generated code looks like.

## Generated class naming and versioning

To keep generated code predictable and aligned with Temporal conventions:

- **Implementation classes** generated from Process and Transaction DSL definitions **must end with `Definition`**.
    - Examples: `LoanDisbursementProcessDefinition`, `KycCheckTransactionDefinition`.
- **Temporal interfaces** generated for those implementations **must not** end with `Definition`; they follow Temporal
  naming (`Workflow`, `Activity`).
    - Examples: `LoanDisbursementProcessWorkflow`, `KycCheckTransactionActivity`.
- **AST/intermediate model classes** use a different suffix to avoid confusion: `ProcessDescriptor`,
  `TransactionDescriptor`, `FunctionDescriptor`.

### Versioned packages

Every generated class is placed in a package that encodes the current git commit:

- Gradle task exposes a `basePackage` property (for example `com.dsl`).
- The compiler appends `.v{shortHash}` using the current short commit hash, e.g. `aty783`, producing `com.dsl.vaty783`.
- If the hash would begin with a digit or contain characters that are illegal in a Java identifier segment, the compiler
  normalizes it — typically by prefixing `v` and replacing invalid characters — while keeping the value deterministic
  and collision-resistant.

### Version accessor

Every generated Temporal interface exposes a version accessor:

- Workflow interfaces get a `@QueryMethod String getVersion()`.
- Activity interfaces get an `@ActivityMethod String getVersion()` (activities do not support queries).

The generated `*Definition` implementation returns the same commit string, for example `"aty783"`. Function DSL
definitions are not generated into Temporal classes, so they do not expose this accessor.

### `dsl()` accessor on Process definitions

Every generated `*ProcessDefinition` implementation exposes a `DslObject dsl()` accessor that returns the source DSL
definition object. Its `execute(...)` method delegates to `GlobalManager.runProcessDsl(dsl(), ctx)`, so the same logic
is used in Preview, Explain, and production Run modes.

## Gradle DSL module

The DSL lives in a dedicated Gradle module. The Java plugin is applied so that normal test classes can be compiled, but
the compact DSL source files are compiled by the custom DSL compiler rather than `javac`.

### `dsl-examples/build.gradle`

```gradle
plugins {
    id 'java'
}

dependencies {
    dslImplementation project(':dsl-api')
    dslImplementation project(':dsl-codegen')

    testImplementation project(':dsl-api')
    testImplementation project(':dsl-codegen')
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.0'
}

sourceSets {
    dsl {
        java {
            srcDirs = ['src']
        }
    }
    test {
        java {
            srcDirs = ['test']
        }
    }
}

// DSL files are JEP-512 compact source files; they are compiled here by the custom DSL compiler.
tasks.named('compileDslJava') {
    enabled = false
}

tasks.register('compileDsl', JavaExec) {
    group = 'build'
    description = 'Compiles DSL compact source files with DslCompiler'
    dependsOn project(':dsl-api').tasks.jar
    dependsOn project(':dsl-codegen').tasks.jar
    classpath = sourceSets.dsl.runtimeClasspath + sourceSets.dsl.compileClasspath
    mainClass = 'com.example.dsl.codegen.DslCompiler'
    args 'src', 'build/dsl-classes'
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.named('classes') {
    dependsOn 'compileDsl'
}

tasks.named('test') {
    dependsOn 'compileDsl'
}
```

Typed input/output records and `@Helper` classes referenced by the DSL live in normal Java modules (for example a shared
`dsl-model` module or the main application module) and are added as `dslImplementation` dependencies when needed.

### Versioning wiring

The compiler receives the `basePackage` and the current short git commit hash from the Gradle task. The task can resolve
the hash with `git rev-parse --short HEAD`; if the working tree is dirty, it can append a marker or fail the build,
depending on the `strictVersioning` setting.

## Generation process

The code generator determines the version to embed from the current git commit. The Gradle task supplies both the
`basePackage` and the short commit hash; if a clean hash is unavailable, the compiler fails the build or falls back to a
configured `fallbackVersion`, depending on `strictVersioning`.

1. **Scanning**: The DSL compiler scans the DSL module's `/src` folder and collects every compact source file that
   declares a `List<DslObject> define()` method.

2. **Parsing**: For each DSL file, the `define()` method is analyzed to extract builder calls. This yields an
   intermediate model:
    - `ProcessDescriptor`: name, task queue, input/output types, compensation block, execution logic.
    - `TransactionDescriptor`: name, task queue, retry policy, input/output types, compensation block, execution logic.
    - `FunctionDescriptor`: name, input/output types or parameter schema, execution logic. No Temporal mapping is
      produced.

3. **Validation**:
    - No duplicate process/transaction/function names.
    - For parameter-based definitions, parameters referenced in `execute` must be registered in `.parameters()`.
    - For typed definitions, `.input(...)`/`.output(...)` types must exist and be valid records annotated with `@Json`.
    - `runHelper` names must exist in `HelperRegistry` with matching generic input/output types or parameter schemas.
      For no-argument calls, the compiler also verifies that the required fields are available in the calling context.
    - `runTransaction` calls in a Process must refer to existing Transaction definitions with matching types or
      parameter schemas.
    - No cyclic dependencies: a Process may call Transactions, Helpers, and Functions; a Transaction may call Helpers
      and Functions; Helpers and Functions may call Helpers and Functions; no entity other than a Process may call
      Transactions.
    - Compensation blocks may only call Helpers and Functions, not Transactions or Processes.

4. **Code Generation**:
    - For each **Process**, generate:
        - A versioned package (`{basePackage}.v{commit}`) for all classes produced from this DSL module.
        - A `@WorkflowInterface` with a `@WorkflowMethod` named `execute` accepting `Context<IN>` and returning
          `Context<OUT>`, plus a `@QueryMethod String getVersion()`.
        - An implementation named `*ProcessDefinition` that exposes the source `DslObject` through `dsl()` and delegates
          execution to `GlobalManager.runProcessDsl(dsl(), ctx)`.
        - Saga wiring: if the Process or any referenced Transaction defines compensation, the generated workflow catches
          failures, invokes completed transaction compensations in reverse order, and finally invokes the process-level
          compensation block through `GlobalManager.compensateProcess(dsl(), ctx, failure)`.
    - For each **Transaction**, generate:
        - An `@ActivityInterface` with an `@ActivityMethod` named `execute` accepting `Context<IN>` and returning
          `Context<OUT>`, plus an `@ActivityMethod String getVersion()`.
        - If a `.compensation(...)` block is declared, add an `@ActivityMethod` named `compensate` accepting
          `Context<IN>` and returning `Context<IN>` (or the declared output type, as configured).
        - An implementation named `*TransactionDefinition` that delegates execution to
          `GlobalManager.executeTransaction(name, ctx)` and compensation to
          `GlobalManager.compensateTransaction(name, ctx)`.
    - For each **Function**, **do not** generate Temporal classes. Register the compiled `DslObject` in `HelperRegistry`
      so it can be invoked via `runHelper`.
    - Generate startup registration code (e.g. SPI registrars or `@PostConstruct` hooks) that registers the source
      `DslObject` of every generated Process/Transaction in the appropriate per-entity registry and every DSL Function
      in `HelperRegistry`, making them resolvable through `GlobalManager`. The generated `*Definition` classes
      themselves are used only by Temporal workers and are not stored in the registries.

## Generated code examples

### Typed Transaction

```java
@ActivityInterface
public interface KycCheckTransactionActivity {

    @ActivityMethod
    String getVersion();

    @ActivityMethod
    Context<KycOut> execute(Context<KycIn> input);
}

public class KycCheckTransactionDefinition
    implements KycCheckTransactionActivity, Executable<KycIn, KycOut> {

    private static final String DSL_VERSION = "aty783";
    private final GlobalManager manager = GlobalManager.getInstance();

    @Override
    public String getVersion() {
        return DSL_VERSION;
    }

    @Override
    public Context<KycOut> execute(Context<KycIn> ctx) {
        return manager.executeTransaction("KYC_CHECK", ctx);
    }

    @Override
    public Context<KycOut> preview(Context<KycIn> ctx) {
        return manager.previewTransaction("KYC_CHECK", ctx);
    }

    @Override
    public Context<KycOut> explain(Context<KycIn> ctx) {
        return manager.explainTransaction("KYC_CHECK", ctx);
    }
}
```

### Compensatable, parameter-based Transaction

```java
@ActivityInterface
public interface DebitFundingTransactionActivity {

    @ActivityMethod
    String getVersion();

    @ActivityMethod
    Context<Map<String, Object>> execute(Context<Map<String, Object>> params);

    @ActivityMethod
    Context<Map<String, Object>> compensate(Context<Map<String, Object>> params);
}

public class DebitFundingTransactionDefinition
    implements DebitFundingTransactionActivity,
               Executable<Map<String, Object>, Map<String, Object>> {

    private static final String DSL_VERSION = "aty783";
    private final GlobalManager manager = GlobalManager.getInstance();

    @Override
    public String getVersion() {
        return DSL_VERSION;
    }

    @Override
    public Context<Map<String, Object>> execute(Context<Map<String, Object>> ctx) {
        return manager.executeTransaction("DEBIT_FUNDING", ctx);
    }

    @Override
    public Context<Map<String, Object>> compensate(Context<Map<String, Object>> ctx) {
        return manager.compensateTransaction("DEBIT_FUNDING", ctx);
    }

    @Override
    public Context<Map<String, Object>> preview(Context<Map<String, Object>> ctx) {
        return manager.previewTransaction("DEBIT_FUNDING", ctx);
    }

    @Override
    public Context<Map<String, Object>> explain(Context<Map<String, Object>> ctx) {
        return manager.explainTransaction("DEBIT_FUNDING", ctx);
    }
}
```

### Generated Process

```java
import com.example.dsl.DslObject;

@WorkflowInterface
public interface LoanDisbursementProcessWorkflow {

    @QueryMethod
    String getVersion();

    @WorkflowMethod
    Context<LoanOut> execute(Context<LoanIn> input);
}

public class LoanDisbursementProcessDefinition
    implements LoanDisbursementProcessWorkflow, Executable<LoanIn, LoanOut> {

    private static final String DSL_VERSION = "aty783";
    private final GlobalManager manager = GlobalManager.getInstance();

    @Override
    public String getVersion() {
        return DSL_VERSION;
    }

    @Override
    public Context<LoanOut> execute(Context<LoanIn> ctx) {
        return manager.runProcessDsl(dsl(), ctx);
    }

    @Override
    public Context<LoanOut> preview(Context<LoanIn> ctx) {
        return execute(ctx);
    }

    @Override
    public Context<LoanOut> explain(Context<LoanIn> ctx) {
        return execute(ctx)
            .withMetadata(Map.of("description", "Loan disbursement process"));
    }

    @Override
    public DslObject dsl() {
        // Inserted by the code generator; reflects the user-authored DSL
        return Dsl.process("LoanDisbursementProcess")
            .taskQueue("loan-processing")
            .version("1.0.0")
            .input(LoanIn.class)
            .output(LoanOut.class)
            .compensation(ctx -> { ... })
            .execute(ctx -> { ... })
            .build();
    }
}
```

The generated `*Definition` delegates execution to the source `DslObject` returned by `dsl()`. Preview/Explain modes can
execute it directly, while production uses the same `DslObject` through the generated workflow.

## Helper & Function integration

Generated code does **not** embed helper or function logic. Helpers remain in their original normal Java classes, and
functions remain as compiled `DslObject`s. A generated Process delegates its own execution to
`GlobalManager.runProcessDsl(dsl(), ctx)`; the returned `DslObject` calls helpers/functions via `ctx.runHelper(...)` and
transactions via `ctx.runTransaction(...)`, which `GlobalManager` resolves and dispatches through the appropriate
runner.

Function calls from DSL code follow the exact same path as helper calls; the only difference is that the name was
originally declared with `Dsl.function(...)` rather than `@Helper`.

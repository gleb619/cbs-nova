# Module Structure

← [Back to TDD](../tdd.md)

## 14. Module Structure

```
root/
├── app/                         ← Spring Boot (Java 25)
├── dsl/                         ← Java DSL runtime (builders, registry, reflective execution)
├── dsl-api/                     ← Java interfaces, records, POJOs shared by dsl and backend
├── dsl-codegen/                 ← Java APT annotation processor + Temporal code generator
├── temporal-core/               ← Workflow + Activity base classes (Java)
├── bpmn-export/                 ← BPMN 2.0 XML generation from DSL model
├── mass-operation-core/         ← MassOp workflow + activity + scheduler
└── build.gradle
```

---

### File Trees

```
app/src/main/java/
├── api/
│   ├── EventController.java
│   ├── MassOperationController.java
│   ├── BpmnController.java
│   └── DevDslController.java              // @Profile("dev")
├── service/
│   ├── EventService.java
│   ├── WorkflowResolver.java
│   ├── WorkflowExecutor.java
│   ├── ContextEvaluator.java
│   ├── ContextEncryptionService.java
│   ├── MassOperationService.java
│   ├── MassOperationScheduler.java
│   ├── SignalEmitter.java
│   └── DslVersionService.java
├── temporal/
│   ├── EventWorkflow.java
│   ├── EventWorkflowImpl.java
│   ├── TransactionActivity.java
│   ├── TransactionActivityImpl.java
│   ├── MassOpWorkflow.java
│   ├── MassOpWorkflowImpl.java
│   └── MassOpItemActivity.java
├── state/
│   ├── WorkflowExecutionRepository.java
│   ├── EventExecutionRepository.java
│   ├── WorkflowTransitionLogRepository.java
│   ├── MassOperationExecutionRepository.java
│   ├── MassOperationItemRepository.java
│   └── ExecutionContextImpl.java
└── dsl/
    └── DslLoader.java

dsl-api/src/main/java/
├── WorkflowDefinition.java        // interface — runtime contract for workflows
├── EventDefinition.java           // interface — runtime contract for events
├── TransactionDefinition.java     // interface — runtime contract for transactions
├── HelperDefinition.java          // interface — runtime contract for helpers
├── ConditionDefinition.java       // interface — runtime contract for conditions
├── MassOperationDefinition.java   // interface — runtime contract for mass operations
├── TransitionRule.java            // record
├── ExecutionResult.java           // record
├── HelperInput.java               // interface
├── HelperOutput.java              // interface
├── HelperFunction.java            // interface — user-facing contract for @DslComponent helpers
├── TransactionFunction.java       // interface — user-facing contract for @DslComponent transactions
├── ConditionFunction.java         // interface — user-facing contract for @DslComponent conditions
├── Signal.java                    // record
├── SignalType.java                // enum
├── Action.java                    // enum
└── context/
    ├── BaseContext.java           // interface
    ├── ParameterContext.java      // interface
    ├── EnrichmentContext.java     // interface
    ├── TransactionContext.java    // interface
    ├── FinishContext.java         // interface
    └── MassOperationContext.java  // interface

dsl/src/main/java/
├── WorkflowBuilder.java
├── EventBuilder.java
├── TransactionBuilder.java
├── HelperBuilder.java
├── ConditionBuilder.java
├── MassOperationBuilder.java
├── SignalBuilder.java
├── TriggerBuilder.java
├── StubWorkflowGenerator.java
├── ConditionDsl.java
├── StepHandle.java
├── DslRegistry.java
├── ReflectiveWorkflow.java        // generic workflow wrapper for REFLECTED mode
├── ReflectiveActivity.java        // generic activity wrapper for REFLECTED mode
└── DevDslEvaluator.java           // lenient dev execution of raw .java DSL

dsl-codegen/src/main/java/
├── DslComponentProcessor.java     // Layer 1: reads @DslComponent on *Function, generates *Definition + SPI
├── DslCompiler.java               // Layer 2: parses .java DSL files, generates Event/Workflow/MassOp Definition
├── DefinitionGenerator.java       // Layer 2: generates *Definition implementations from DSL AST
├── TemporalWorkflowGenerator.java // Layer 3-prod: generates Temporal workflow impls from *Definition
├── TemporalActivityGenerator.java // Layer 3-prod: generates Temporal activity impls from *Definition
├── ImplRegistrationGenerator.java // generates SPI ImplRegistrationProvider
└── ParameterMetadataExtractor.java // extracts required/optional params from @Json records

bpmn-export/src/main/java/
├── BpmnExporter.java
├── StaticBpmnGenerator.java
├── DynamicBpmnGenerator.java
└── BpmnHeatmapOverlay.java

temporal-core/src/main/java/
├── BaseTransactionActivity.java
└── WorkflowContextBridge.java
```

---

## Dependency Graph

```
app → dsl → dsl-api
app → dsl-api
app → temporal-core
app → bpmn-export
app → mass-operation-core

# Compile-time only (annotation processor)
dsl-codegen → dsl-api

# Full runtime dependencies
dsl → dsl-api
starter → dsl
temporal-core → dsl-api
bpmn-export → dsl-api
mass-operation-core → dsl-api
```

**Code generation pipeline (compile time only):**

```
@DslComponent *Function classes  ──►  dsl-codegen (Layer 1)
                                      generates *Definition + SPI registration

.java DSL files (event/workflow)  ──►  dsl-codegen (Layer 2)
                                      generates Event/Workflow/MassOp Definition

*Definition classes              ──►  dsl-codegen (Layer 3)
                                      generates Temporal Workflow + Activity
```

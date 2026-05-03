# Module Structure

← [Back to TDD](../tdd.md)

## 14. Module Structure

```
root/
├── app/                         ← Spring Boot (Java 25)
├── dsl/                         ← Kotlin DSL logic (builders, registry, runtime execution)
├── dsl-api/                     ← Java interfaces, records, POJOs shared by dsl and backend
├── temporal-core/               ← Workflow + Activity base classes (Java)
├── bpmn-export/                 ← BPMN 2.0 XML generation from DSL model
├── mass-operation-core/         ← MassOp workflow + activity + scheduler
└── build.gradle.kts
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
├── WorkflowDefinition.java        // interface
├── EventDefinition.java           // interface
├── TransactionDefinition.java     // interface
├── HelperDefinition.java          // interface
├── ConditionDefinition.java       // interface
├── MassOperationDefinition.java   // interface
├── TransitionRule.java            // record
├── ExecutionResult.java           // record
├── HelperInput.java               // interface
├── HelperOutput.java              // interface
├── HelperFunction.java            // interface
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

dsl/src/main/kotlin/
├── WorkflowBuilder.kt
├── EventBuilder.kt
├── TransactionBuilder.kt
├── HelperBuilder.kt
├── ConditionBuilder.kt
├── MassOperationBuilder.kt
├── SignalBuilder.kt
├── TriggerBuilder.kt
├── StubWorkflowGenerator.kt
├── ConditionDsl.kt
├── StepHandle.kt
├── DslRegistry.kt
└── DevDslEvaluator.kt             // lenient dev execution of raw .kts

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

`app → dsl → dsl-api` · `app → dsl-api` · `app → temporal-core` · `app → bpmn-export` · `app → mass-operation-core`

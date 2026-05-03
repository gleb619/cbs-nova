# Module Structure

← [Back to TDD](../tdd.md)

## 14. Module Structure

```
root/
├── app/
├── dsl/
│   ├── dsl-api/
│   ├── dsl-compiler/
│   └── dsl-runtime/
├── temporal-core/
├── bpmn-export/
├── mass-operation-core/          ← NEW: MassOp workflow + activity + scheduler
└── build.gradle.kts

app/src/main/java/
├── api/
│   ├── EventController.java
│   ├── MassOperationController.java      // NEW
│   ├── BpmnController.java
│   └── DevDslController.java
├── service/
│   ├── EventService.java
│   ├── WorkflowResolver.java
│   ├── WorkflowExecutor.java
│   ├── ContextEvaluator.java
│   ├── ContextEncryptionService.java
│   ├── MassOperationService.java         // NEW
│   ├── MassOperationScheduler.java       // NEW: cron + trigger management
│   ├── SignalEmitter.java                // NEW
│   └── DslVersionService.java
├── temporal/
│   ├── EventWorkflow.java
│   ├── EventWorkflowImpl.java
│   ├── TransactionActivity.java
│   ├── TransactionActivityImpl.java
│   ├── MassOpWorkflow.java               // NEW
│   ├── MassOpWorkflowImpl.java           // NEW
│   └── MassOpItemActivity.java           // NEW
├── state/
│   ├── WorkflowExecutionRepository.java
│   ├── EventExecutionRepository.java
│   ├── WorkflowTransitionLogRepository.java
│   ├── MassOperationExecutionRepository.java  // NEW
│   ├── MassOperationItemRepository.java       // NEW
│   └── ExecutionContextImpl.java
└── dsl/
    └── DslLoader.java

dsl/dsl-api/src/main/kotlin/
├── WorkflowDefinition.kt
├── TransitionRule.kt
├── TransitionContext.kt                  // NEW: ctx for transition closures
├── EventDefinition.kt
├── TransactionDefinition.kt
├── HelperDefinition.kt
├── ConditionDefinition.kt
├── MassOperationDefinition.kt           // NEW
├── SignalDefinition.kt                  // NEW
├── TriggerDefinition.kt                 // NEW
├── SourceDefinition.kt                  // NEW
├── LockDefinition.kt                    // NEW
├── ItemDefinition.kt                    // NEW
├── ContextBlock.kt
├── DisplayBlock.kt
├── FinishBlock.kt
├── context/
│   ├── BaseContext.kt
│   ├── ParameterContext.kt
│   ├── EnrichmentContext.kt
│   ├── TransactionContext.kt
│   ├── FinishContext.kt
│   └── MassOperationContext.kt          // NEW
├── ExecutionResult.kt
├── HelperInput.kt
├── HelperOutput.kt
├── HelperFunction.kt
├── Signal.kt                            // NEW
├── SignalType.kt                        // NEW
└── Action.kt

dsl/dsl-runtime/src/main/kotlin/
├── WorkflowBuilder.kt
├── EventBuilder.kt
├── TransactionBuilder.kt
├── HelperBuilder.kt
├── ConditionBuilder.kt
├── MassOperationBuilder.kt              // NEW
├── SignalBuilder.kt                     // NEW
├── TriggerBuilder.kt                    // NEW
├── StubWorkflowGenerator.kt
├── ConditionDsl.kt
├── StepHandle.kt
└── DslRegistry.kt
```

---

### Detailed File Trees (v0.4 baseline)

```
root/
├── app/                         ← Spring Boot (Java 25)
├── dsl/
│   ├── dsl-api/                 ← DSL interfaces (Kotlin)
│   ├── dsl-compiler/            ← Gradle tasks: download, compile, validate, publish
│   └── dsl-runtime/             ← runtime loader + lenient dev-mode execution
├── temporal-core/               ← Workflow + Activity base classes (Java)
├── bpmn-export/                 ← BPMN 2.0 XML generation from DSL model
└── build.gradle.kts

app/src/main/java/
├── api/
│   ├── EventController.java
│   ├── BpmnController.java
│   └── DevDslController.java                  // @Profile("dev")
├── service/
│   ├── EventService.java
│   ├── WorkflowResolver.java
│   ├── WorkflowExecutor.java
│   ├── ContextEvaluator.java
│   ├── ContextEncryptionService.java          // encrypt/decrypt JSONB fields
│   └── DslVersionService.java
├── temporal/
│   ├── EventWorkflow.java
│   ├── EventWorkflowImpl.java
│   ├── TransactionActivity.java
│   └── TransactionActivityImpl.java
├── state/
│   ├── WorkflowExecutionRepository.java
│   ├── EventExecutionRepository.java
│   ├── WorkflowTransitionLogRepository.java
│   └── ExecutionContextImpl.java
└── dsl/
    └── DslLoader.java

dsl/dsl-api/src/main/kotlin/
├── WorkflowDefinition.kt
├── TransitionRule.kt
├── EventDefinition.kt
├── TransactionDefinition.kt
├── HelperDefinition.kt
├── ConditionDefinition.kt
├── ContextBlock.kt
├── DisplayBlock.kt
├── FinishBlock.kt
├── context/
│   ├── BaseContext.kt
│   ├── ParameterContext.kt
│   ├── EnrichmentContext.kt
│   ├── TransactionContext.kt
│   └── FinishContext.kt
├── ExecutionResult.kt
├── HelperInput.kt
├── HelperOutput.kt
├── HelperFunction.kt
└── Action.kt

dsl/dsl-runtime/src/main/kotlin/
├── DslArtifactLoader.kt                        // load compiled DSL JAR/classes for prod runtime
├── DevDslEvaluator.kt                          // lenient dev execution of raw .kts (no compile/package)
├── DevDslRegistryAdapter.kt                    // adapter shared with API contracts
└── ExecutionMode.kt                            // STRICT / LENIENT mode handling

dsl/dsl-compiler/src/main/kotlin/
├── tasks/
│   ├── DownloadDslTask.kt
│   ├── CompileDslTask.kt
│   ├── ValidateDslTask.kt
│   └── PublishDslToMavenLocalTask.kt
├── ImportResolver.kt
├── SemanticValidator.kt
└── KtsCompiler.kt

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

## Migration Notes (hard rename)

- `dsl` root module is split into nested Gradle submodules: `dsl/dsl-api`, `dsl/dsl-compiler`, `dsl/dsl-runtime`.
- Existing legacy package locations under old `dsl/src/main/kotlin/` are deprecated and should be removed once imports
  are switched.
- Deprecated runtime-builder classes (`WorkflowBuilder`, `EventBuilder`, `TransactionBuilder`, `DslRegistry`) should be
  migrated to `dsl/dsl-api` contracts + `dsl/dsl-runtime` adapters, then deleted from legacy paths.

# T88 — DslAutoConfiguration ExternalCallListener registration tests

## Goal
Add unit tests for `DslAutoConfiguration.registerExternalCallListeners()` covering the cases where listener beans exist, are absent, or the tracker instance is not yet initialized. Locks the Spring Boot auto-wiring contract between listeners and `ExternalCallTracker`.

## Tier
backend

## Files to create / modify
- Modify: `backend/starter/src/test/java/cbs/nova/starter/DslAutoConfigurationTest.java`
- Read only: `backend/starter/src/main/java/cbs/nova/starter/DslAutoConfiguration.java`, `backend/starter/src/main/java/cbs/nova/starter/ExternalCallListener.java`, `backend/starter/src/main/java/cbs/nova/starter/ExternalCallTracker.java`

## Acceptance criteria
- Test that `ExternalCallListener` beans are registered with `ExternalCallTracker` when the tracker instance exists.
- Test that no error occurs when no listener beans are present.
- Test that listeners are silently skipped when `ExternalCallTracker.getInstance()` returns null.
- Existing `DslAutoConfigurationTest` cases continue to pass.
- `./gradlew :starter:test --tests '*DslAutoConfigurationTest*'` passes.

## Build / test commands
```bash
cd backend
./gradlew spotlessApply
./gradlew :starter:test --tests '*DslAutoConfigurationTest*'
```

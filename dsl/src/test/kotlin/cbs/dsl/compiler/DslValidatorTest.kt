package cbs.dsl.compiler

import cbs.dsl.runtime.DslRegistry
import cbs.dsl.runtime.event
import cbs.dsl.runtime.workflow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DslValidatorTest {
    private val validator = DslValidator()

    @Test
    fun `shouldPassForValidWorkflow`() {
        val registry = DslRegistry()
        val evt = event("test-event") {}
        registry.register(evt)
        val wf =
            workflow("test-wf") {
                states("INIT", "DONE")
                initial("INIT")
                terminalStates("DONE")
            }
        registry.register(wf)

        val errors = validator.validate(registry, "test.kts")

        assertTrue(errors.isEmpty(), "Expected no errors but got: $errors")
    }

    @Test
    fun `shouldFailWhenInitialStateNotInStates`() {
        val registry = DslRegistry()
        val wf =
            workflow("test-wf") {
                states("A")
                initial("X")
                terminalStates("A")
            }
        registry.register(wf)

        val errors = validator.validate(registry, "test.kts")

        assertEquals(1, errors.size)
        assertEquals("test.kts", errors[0].file)
        assertTrue(errors[0].message.contains("initial state 'X' not in states"))
    }

    @Test
    fun `shouldFailWhenTerminalStateNotInStates`() {
        val registry = DslRegistry()
        val wf =
            workflow("test-wf") {
                states("A")
                initial("A")
                terminalStates("Z")
            }
        registry.register(wf)

        val errors = validator.validate(registry, "test.kts")

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("terminal state 'Z' not in states"))
    }

    @Test
    fun `shouldFailWhenTransitionFromUnknownState`() {
        val registry = DslRegistry()
        val evt = event("test-event") {}
        registry.register(evt)
        val wf =
            workflow("test-wf") {
                states("A", "B")
                initial("A")
                terminalStates("B")
                transition(
                    from = "Z",
                    to = "B",
                    on = cbs.dsl.api.Action.SUBMIT,
                    event = evt,
                )
            }
        registry.register(wf)

        val errors = validator.validate(registry, "test.kts")

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("transition from 'Z' references unknown state"))
    }

    @Test
    fun `shouldFailWhenTransitionToUnknownState`() {
        val registry = DslRegistry()
        val evt = event("test-event") {}
        registry.register(evt)
        val wf =
            workflow("test-wf") {
                states("A", "B")
                initial("A")
                terminalStates("B")
                transition(
                    from = "A",
                    to = "Z",
                    on = cbs.dsl.api.Action.SUBMIT,
                    event = evt,
                )
            }
        registry.register(wf)

        val errors = validator.validate(registry, "test.kts")

        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("transition to 'Z' references unknown state"))
    }

    @Test
    fun `shouldPassForEmptyRegistry`() {
        val registry = DslRegistry()

        val errors = validator.validate(registry, "test.kts")

        assertTrue(errors.isEmpty(), "Expected no errors for empty registry but got: $errors")
    }
}

package cbs.dsl.runtime

import cbs.dsl.api.Action
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TransitionScopeTest {
    @Test
    @DisplayName("should build transition rule when infix 'on' and invoke used")
    fun shouldBuildTransitionRuleWhenInfixOnAndInvokeUsed() {
        val scope = TransitionScope()

        with(scope) {
            ("A" to "B" on Action.SUBMIT) {}
        }

        assertEquals(1, scope.rules.size)
        val rule = scope.rules[0]
        assertEquals("A", rule.from)
        assertEquals("B", rule.to)
        assertEquals(Action.SUBMIT, rule.on)
        assertEquals("A_B_SUBMIT", rule.event.code)
    }

    @Test
    @DisplayName("should capture event block when transactions declared in closure")
    fun shouldCaptureEventBlockWhenTransactionsDeclaredInClosure() {
        val scope = TransitionScope()

        with(scope) {
            ("A" to "B" on Action.SUBMIT) {
                transactions {
                    // transaction block
                }
            }
        }

        assertEquals(1, scope.rules.size)
        val rule = scope.rules[0]
        assertNotNull(rule.event.transactionsBlock)
    }

    @Test
    @DisplayName("should set onFault block when onFault called")
    fun shouldSetOnFaultBlockWhenOnFaultCalled() {
        val scope = TransitionScope()

        with(scope) {
            ("A" to "B" on Action.SUBMIT) {} onFault {
                // fault block
            }
        }

        assertEquals(1, scope.rules.size)
        val rule = scope.rules[0]
        assertNotNull(rule.onFaultBlock)
    }

    @Test
    @DisplayName("should amend last rule when onFault called after invoke")
    fun shouldAmendLastRuleWhenOnFaultCalledAfterInvoke() {
        val scope = TransitionScope()

        with(scope) {
            ("A" to "B" on Action.SUBMIT) {}
            ("B" to "C" on Action.APPROVE) {}

            // Apply onFault to the last transition (second one)
            // The onFault method amends rules[rules.lastIndex]
            // So we capture the builder from the second transition
            val second = ("C" to "D" on Action.CLOSE) {}
            second onFault {
                // fault block
            }
        }

        assertEquals(3, scope.rules.size)
        // onFault amends the LAST rule in the list
        assertNull(scope.rules[0].onFaultBlock, "First rule should not have onFaultBlock")
        assertNull(scope.rules[1].onFaultBlock, "Second rule should not have onFaultBlock")
        assertNotNull(scope.rules[2].onFaultBlock, "Third (last) rule should have onFaultBlock")
    }

    @Test
    @DisplayName("should keep default onFault state when not overridden")
    fun shouldKeepDefaultOnFaultStateWhenNotOverridden() {
        val scope = TransitionScope()

        with(scope) {
            ("A" to "B" on Action.SUBMIT) {}
        }

        assertEquals(1, scope.rules.size)
        val rule = scope.rules[0]
        assertEquals("FAULTED", rule.onFault)
    }

    @Test
    @DisplayName("should register multiple rules when multiple transitions declared")
    fun shouldRegisterMultipleRulesWhenMultipleTransitionsDeclared() {
        val scope = TransitionScope()

        with(scope) {
            ("A" to "B" on Action.SUBMIT) {}
            ("B" to "C" on Action.APPROVE) {}
            ("C" to "D" on Action.CLOSE) {}
        }

        assertEquals(3, scope.rules.size)
    }
}

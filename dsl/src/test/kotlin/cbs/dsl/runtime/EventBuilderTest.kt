package cbs.dsl.runtime

import cbs.dsl.api.ParameterDefinition
import cbs.dsl.api.context.DisplayScope
import cbs.dsl.api.context.EnrichmentContext
import cbs.dsl.api.context.FinishContext
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventBuilderTest {
    @Test
    @DisplayName("shouldHaveEmptyParametersWhenNoneDeclared")
    fun `should have empty parameters when none declared`() {
        val event =
            event("TEST_EVENT") {
                context { }
            }

        assertTrue(event.parameters.isEmpty())
    }

    @Test
    @DisplayName("shouldRegisterRequiredParameterWhenRequiredCalled")
    fun `should register required parameter when required called`() {
        val event =
            event("TEST_EVENT") {
                parameters {
                    required("accountId")
                }
            }

        assertEquals(1, event.parameters.size)
        assertEquals(ParameterDefinition("accountId", true), event.parameters[0])
    }

    @Test
    @DisplayName("shouldRegisterOptionalParameterWhenOptionalCalled")
    fun `should register optional parameter when optional called`() {
        val event =
            event("TEST_EVENT") {
                parameters {
                    optional("description")
                }
            }

        assertEquals(1, event.parameters.size)
        assertEquals(ParameterDefinition("description", false), event.parameters[0])
    }

    @Test
    @DisplayName("shouldPreserveParameterOrderWhenMultipleDeclared")
    fun `should preserve parameter order when multiple declared`() {
        val event =
            event("TEST_EVENT") {
                parameters {
                    required("first")
                    optional("second")
                    required("third")
                }
            }

        assertEquals(3, event.parameters.size)
        assertEquals(ParameterDefinition("first", true), event.parameters[0])
        assertEquals(ParameterDefinition("second", false), event.parameters[1])
        assertEquals(ParameterDefinition("third", true), event.parameters[2])
    }

    @Test
    @DisplayName("shouldInvokeContextBlockWhenContextSet")
    fun `should invoke context block when context set`() {
        var invoked = false
        val event =
            event("TEST_EVENT") {
                context { ctx ->
                    invoked = true
                    assertNotNull(ctx)
                }
            }

        val testContext =
            EnrichmentContext(
                eventCode = "TEST",
                workflowExecutionId = 1L,
                performedBy = "test",
                dslVersion = "1.0",
                eventParameters = emptyMap(),
            )
        event.contextBlock(testContext)
        assertTrue(invoked)
    }

    @Test
    @DisplayName("shouldInvokeDisplayBlockWithLabelWhenDisplaySet")
    fun `should invoke display block with label when display set`() {
        val event =
            event("TEST_EVENT") {
                display { scope ->
                    scope.label("amount", 100.0)
                    scope.label("currency", "USD")
                }
            }

        val displayScope = DisplayScope()
        event.displayBlock(displayScope)

        assertEquals(2, displayScope.labels.size)
        assertEquals("amount" to 100.0, displayScope.labels[0])
        assertEquals("currency" to "USD", displayScope.labels[1])
    }

    @Test
    @DisplayName("shouldHaveNullTransactionsBlockWhenNotSet")
    fun `should have null transactions block when not set`() {
        val event =
            event("TEST_EVENT") {
                context { }
            }

        assertNull(event.transactionsBlock)
    }

    @Test
    @DisplayName("shouldInvokeFinishBlockWithNullExceptionOnSuccess")
    fun `should invoke finish block with null exception on success`() {
        var invoked = false
        var capturedException: Throwable? = null

        val event =
            event("TEST_EVENT") {
                finish { ctx, ex ->
                    invoked = true
                    capturedException = ex
                    assertNotNull(ctx)
                }
            }

        val testContext =
            FinishContext(
                eventCode = "TEST",
                workflowExecutionId = 1L,
                performedBy = "test",
                dslVersion = "1.0",
                eventParameters = emptyMap(),
                displayData = emptyMap(),
            )
        event.finishBlock(testContext, null)

        assertTrue(invoked)
        assertNull(capturedException)
    }

    @Test
    @DisplayName("shouldInvokeFinishBlockWithExceptionOnFailure")
    fun `should invoke finish block with exception on failure`() {
        var invoked = false
        var capturedException: Throwable? = null
        val testException = RuntimeException("test error")

        val event =
            event("TEST_EVENT") {
                finish { ctx, ex ->
                    invoked = true
                    capturedException = ex
                    assertNotNull(ctx)
                }
            }

        val testContext =
            FinishContext(
                eventCode = "TEST",
                workflowExecutionId = 1L,
                performedBy = "test",
                dslVersion = "1.0",
                eventParameters = emptyMap(),
                displayData = emptyMap(),
            )
        event.finishBlock(testContext, testException)

        assertTrue(invoked)
        assertEquals(testException, capturedException)
    }

    @Test
    @DisplayName("shouldBuildEventDefinitionWithAllBlocksSet")
    fun `should build event definition with all blocks set`() {
        val event =
            event("FULL_EVENT") {
                parameters {
                    required("accountId")
                    optional("note")
                }
                context { ctx -> }
                display { scope -> scope.label("key", "value") }
                transactions { }
                finish { ctx, ex -> }
            }

        assertEquals("FULL_EVENT", event.code)
        assertEquals(2, event.parameters.size)
        assertNotNull(event.contextBlock)
        assertNotNull(event.displayBlock)
        assertNotNull(event.transactionsBlock)
        assertNotNull(event.finishBlock)
    }
}

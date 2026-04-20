package cbs.dsl.script

import cbs.dsl.api.HelperDefinition
import cbs.dsl.api.context.BaseContext
import cbs.dsl.runtime.HelperBuilder
import cbs.dsl.runtime.MapHelperInput
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HelperDslScopeTest {
    @Test
    @DisplayName("shouldRegisterHelperWhenHelperFunctionCalled")
    fun `should register helper when helper function called`() {
        val scope = TestHelperDslScope()

        val helper =
            scope.helper("TEST_HELPER") {
                execute { ctx -> "result" }
            }

        assertNotNull(helper)
        assertEquals("TEST_HELPER", helper.code)
        assertEquals(1, scope.getRegisteredHelpers().size)
        assertEquals(helper, scope.getRegisteredHelpers()[0])
    }

    @Test
    @DisplayName("shouldRegisterMultipleHelpersWhenCalledTwice")
    fun `should register multiple helpers when called twice`() {
        val scope = TestHelperDslScope()

        val helper1 =
            scope.helper("HELPER_1") {
                execute { ctx -> "result1" }
            }

        val helper2 =
            scope.helper("HELPER_2") {
                execute { ctx -> "result2" }
            }

        assertNotNull(helper1)
        assertNotNull(helper2)
        assertEquals("HELPER_1", helper1.code)
        assertEquals("HELPER_2", helper2.code)
        assertEquals(2, scope.getRegisteredHelpers().size)
        assertEquals(helper1, scope.getRegisteredHelpers()[0])
        assertEquals(helper2, scope.getRegisteredHelpers()[1])
    }

    @Test
    @DisplayName("shouldReturnHelperDefinitionFromHelperFunction")
    fun `should return helper definition from helper function`() {
        val scope = TestHelperDslScope()

        val helper =
            scope.helper("TEST_HELPER") {
                execute { ctx -> "result" }
            }

        assertNotNull(helper)
        assertEquals("TEST_HELPER", helper.code)
        assertTrue(helper is HelperDefinition)
    }

    @Test
    @DisplayName("shouldAllowHelpersBlockToRegisterMultipleHelpers")
    fun `should allow helpers block to register multiple helpers`() {
        val scope = TestHelperDslScope()

        scope.helpers {
            helper("HELPER_1") {
                execute { ctx -> "result1" }
            }
            helper("HELPER_2") {
                execute { ctx -> "result2" }
            }
        }

        assertEquals(2, scope.getRegisteredHelpers().size)
        assertEquals("HELPER_1", scope.getRegisteredHelpers()[0].code)
        assertEquals("HELPER_2", scope.getRegisteredHelpers()[1].code)
    }

    @Test
    @DisplayName("shouldThrowWhenExecuteBlockMissing")
    fun `should throw when execute block missing`() {
        val builder = HelperBuilder("NO_EXEC_HELPER")

        val baseContext = BaseContext("EVT", 1L, "user", "v1")
        val input = MapHelperInput(emptyMap(), baseContext)

        val exception =
            org.junit.jupiter.api.assertThrows<IllegalStateException> {
                builder.execute(input)
            }
        assertTrue(exception.message!!.contains("has no execute block defined"))
    }

    private class TestHelperDslScope : HelperDslScope() {
        fun getRegisteredHelpers(): List<HelperDefinition> = registeredHelpers.toList()
    }
}

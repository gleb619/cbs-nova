package cbs.dsl.script

import cbs.dsl.api.ParameterDefinition
import cbs.dsl.runtime.MassOpBuilder
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MassOperationDslScopeTest {
  @Test
  @DisplayName("shouldRegisterMassOperationWhenMassOperationFunctionCalled")
  fun `should register mass operation when mass operation function called`() {
    val scope = TestMassOperationDslScope()

    scope.massOperation("MASS_1") { item {} }

    assertNotNull(scope.registeredMassOperation)
    assertEquals("MASS_1", scope.registeredMassOperation!!.code)
  }

  @Test
  @DisplayName("shouldReturnMassOperationDefinitionFromMassOperationFunction")
  fun `should return mass operation definition from mass operation function`() {
    val scope = TestMassOperationDslScope()

    val result = scope.massOperation("MASS_1") { item {} }

    assertSame(scope.registeredMassOperation, result)
  }

  @Test
  @DisplayName("shouldThrowWhenMultipleMassOperationBlocksDefined")
  fun `should throw when multiple mass operation blocks defined`() {
    val scope = TestMassOperationDslScope()
    scope.massOperation("MASS_1") { item {} }

    val exception =
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
          scope.massOperation("MASS_2") { item {} }
        }
    assertTrue(exception.message!!.contains("Only one massOperation block"))
  }

  @Test
  @DisplayName("shouldThrowWhenItemBlockMissing")
  fun `should throw when item block missing`() {
    val builder = MassOpBuilder("NO_ITEM")

    val exception = org.junit.jupiter.api.assertThrows<IllegalStateException> { builder.itemBlock }
    assertTrue(exception.message!!.contains("has no item block defined"))
  }

  @Test
  @DisplayName("shouldThrowWhenSourceMissing")
  fun `should throw when source missing`() {
    val builder = MassOpBuilder("NO_SOURCE")

    val exception = org.junit.jupiter.api.assertThrows<IllegalStateException> { builder.source }
    assertTrue(exception.message!!.contains("has no source defined"))
  }

  @Test
  @DisplayName("shouldCollectParametersWhenParametersBlockDefined")
  fun `should collect parameters when parameters block defined`() {
    val scope = TestMassOperationDslScope()

    val result =
        scope.massOperation("MASS_1") {
          parameters {
            required("clientId")
            optional("note")
          }
          item {}
        }

    assertEquals(
        listOf(
            ParameterDefinition("clientId", required = true),
            ParameterDefinition("note", required = false),
        ),
        result.parameters,
    )
  }

  private class TestMassOperationDslScope : MassOperationDslScope()
}

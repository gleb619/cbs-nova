package cbs.dsl.codegen

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.junit.jupiter.api.DisplayName

class RegistrationGeneratorTest {

  private class FakeCodeGenerator : CodeGenerator {
    val files = mutableMapOf<String, String>()

    override fun createNewFile(
        dependencies: Dependencies,
        packageName: String,
        fileName: String,
        extensionName: String,
    ): OutputStream {
      val key = "$packageName/$fileName${if (extensionName.isEmpty()) "" else ".$extensionName"}"
      return object : OutputStream() {
        val bytes = java.io.ByteArrayOutputStream()

        override fun write(b: Int) = bytes.write(b)

        override fun close() {
          files[key] = bytes.toString(StandardCharsets.UTF_8)
        }
      }
    }

    override fun createNewFileByPath(
        dependencies: Dependencies,
        path: String,
        extensionName: String,
    ): OutputStream = throw UnsupportedOperationException()

    override fun associate(
        sources: List<com.google.devtools.ksp.symbol.KSFile>,
        packageName: String,
        fileName: String,
        extensionName: String,
    ) = Unit

    override fun associateByPath(
        sources: List<com.google.devtools.ksp.symbol.KSFile>,
        path: String,
        extensionName: String,
    ) = Unit

    override fun associateWithClasses(
        classes: List<com.google.devtools.ksp.symbol.KSClassDeclaration>,
        packageName: String,
        fileName: String,
        extensionName: String,
    ) = Unit

    override val generatedFile: Collection<java.io.File> = emptyList()
  }

  @Test
  @DisplayName("shouldGenerateImplRegistrationsObjectWithRegisterCalls")
  fun `shouldGenerateImplRegistrationsObjectWithRegisterCalls`() {
    val fake = FakeCodeGenerator()
    val specs =
        listOf(
            RegistrationSpec("com.example", "TxOne", "TX_1", DslInterfaceType.TRANSACTION),
            RegistrationSpec("com.example", "HelperOne", "H_1", DslInterfaceType.HELPER),
        )

    RegistrationGenerator(fake).generate(specs)

    assertTrue(fake.files.containsKey("cbs.dsl.codegen.generated/GeneratedImplRegistrations.kt"))
    val ktContent = fake.files["cbs.dsl.codegen.generated/GeneratedImplRegistrations.kt"]!!
    assertContains(ktContent, "object GeneratedImplRegistrations : ImplRegistrationProvider")
    assertContains(ktContent, "registry.register(TxOne())")
    assertContains(ktContent, "registry.register(HelperOne())")
  }

  @Test
  @DisplayName("shouldGenerateSpiServiceFile")
  fun `shouldGenerateSpiServiceFile`() {
    val fake = FakeCodeGenerator()
    RegistrationGenerator(fake).generate(emptyList())

    assertTrue(fake.files.containsKey("META-INF/services/cbs.dsl.api.ImplRegistrationProvider"))
    val spiContent = fake.files["META-INF/services/cbs.dsl.api.ImplRegistrationProvider"]!!
    assertContains(spiContent, "cbs.dsl.codegen.generated.GeneratedImplRegistrations")
  }
}

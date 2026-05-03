package cbs.dsl.codegen

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*

/**
 * KSP annotation processor that processes [cbs.dsl.api.DslComponent] annotations.
 *
 * Validates annotated classes and collects registration specs for code generation.
 */
class DslComponentProcessor(
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator,
) : SymbolProcessor {

  private val dslComponentFqName = "cbs.dsl.api.DslComponent"
  private val allowedInterfaces =
      setOf(
          "cbs.dsl.api.TransactionDefinition",
          "cbs.dsl.api.HelperDefinition",
          "cbs.dsl.api.ConditionDefinition",
          "cbs.dsl.api.WorkflowDefinition",
          "cbs.dsl.api.MassOperationDefinition",
      )

  override fun process(resolver: Resolver): List<KSAnnotated> {
    val registrations = mutableListOf<RegistrationSpec>()

    resolver
        .getSymbolsWithAnnotation(dslComponentFqName)
        .filterIsInstance<KSClassDeclaration>()
        .forEach { classDecl -> validateAndCollect(classDecl, registrations) }

    if (registrations.isNotEmpty()) {
      RegistrationGenerator(codeGenerator).generate(registrations)
    }

    return emptyList()
  }

  private fun validateAndCollect(
      classDecl: KSClassDeclaration,
      registrations: MutableList<RegistrationSpec>,
  ) {
    val className = classDecl.simpleName.asString()
    val packageName = classDecl.packageName.asString()

    // Validate: must be a class (not interface/object)
    if (classDecl.classKind != ClassKind.CLASS) {
      logger.error(
          "Class '$className' annotated with @DslComponent must be a class, not an interface or object",
          classDecl,
      )
      return
    }

    // Validate: must have public no-arg constructor
    val noArgConstructor =
        classDecl.primaryConstructor
            ?: classDecl.declarations.filterIsInstance<KSFunctionDeclaration>().firstOrNull {
              it.parameters.isEmpty() && it.isConstructor()
            }

    if (noArgConstructor == null) {
      logger.error(
          "Class '$className' annotated with @DslComponent must have a public no-arg constructor",
          classDecl,
      )
      return
    }

    // Validate: must implement exactly one allowed interface
    val implementedInterfaces =
        classDecl.superTypes
            .mapNotNull { it.resolve().declaration as? KSClassDeclaration }
            .filter { it.qualifiedName?.asString() in allowedInterfaces }
            .toList()

    if (implementedInterfaces.isEmpty()) {
      logger.error(
          "Class '$className' must implement exactly one of: ${allowedInterfaces.joinToString(", ")}",
          classDecl,
      )
      return
    }

    if (implementedInterfaces.size > 1) {
      logger.error(
          "Class '$className' implements multiple DSL definition interfaces; must implement exactly one",
          classDecl,
      )
      return
    }

    // Read annotation arguments
    val annotation = classDecl.annotations.find { it.shortName.asString() == "DslComponent" }
    if (annotation == null) {
      logger.error("Class '$className' is not annotated with @DslComponent", classDecl)
      return
    }

    val codeArg = annotation.arguments.find { it.name?.asString() == "code" }?.value as? String
    if (codeArg.isNullOrBlank()) {
      logger.error("@DslComponent.code must not be blank for class '$className'", classDecl)
      return
    }

    // Determine interface type
    val interfaceFqName = implementedInterfaces.first().qualifiedName!!.asString()
    val interfaceType =
        when (interfaceFqName) {
          "cbs.dsl.api.TransactionDefinition" -> DslInterfaceType.TRANSACTION
          "cbs.dsl.api.HelperDefinition" -> DslInterfaceType.HELPER
          "cbs.dsl.api.ConditionDefinition" -> DslInterfaceType.CONDITION
          "cbs.dsl.api.WorkflowDefinition" -> DslInterfaceType.WORKFLOW
          "cbs.dsl.api.MassOperationDefinition" -> DslInterfaceType.MASS_OPERATION
          else -> {
            logger.error("Unsupported DSL interface type: $interfaceFqName", classDecl)
            return
          }
        }

    registrations.add(
        RegistrationSpec(
            packageName = packageName,
            className = className,
            code = codeArg,
            interfaceType = interfaceType,
        )
    )
  }

  private fun KSFunctionDeclaration.isConstructor(): Boolean {
    return this.simpleName.asString() == "<init>"
  }
}

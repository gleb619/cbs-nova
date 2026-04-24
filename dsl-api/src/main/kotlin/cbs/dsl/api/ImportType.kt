package cbs.dsl.api

/**
 * Defines the type of an import directive in a DSL script.
 */
enum class ImportType {
    /**
     * Imported from another DSL script (.kts file).
     */
    DSL,

    /**
     * Imported from compiled Kotlin/Java code (classes annotated with @DslComponent).
     */
    CODE,
}

package cbs.nova.dsl.codegen;

import cbs.nova.dsl.SemanticValidator;
import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.config.SingletonSupport;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.registry.HelperRegistry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

@Getter
@RequiredArgsConstructor
public final class CompileConfig implements SingletonSupport {

  private final Scope scope;
  private final Level logLevel;

  public static CompileConfig compileConfig() {
    return Holder.INSTANCE.compileConfig();
  }

  public static CompileConfig compileConfig(Level logLevel) {
    return Holder.INSTANCE.compileConfig(SingletonScope.of(), logLevel);
  }

  public static CompileConfig compileConfig(Scope scope, Level logLevel) {
    return Holder.INSTANCE.compileConfig(scope, logLevel);
  }

  /* ============= Bean definitions ============= */

  public @NonNull CodegenNaming codegenNaming() {
    return singleton(CodegenNaming::new);
  }

  public @NonNull SourceCompiler sourceCompiler() {
    return singleton(() -> new SourceCompiler(
            logLevel, definitionProviderGenerator()));
  }

  public @NonNull DslSourceCompiler dslSourceCompiler() {
    return singleton(() -> new DslSourceCompiler(sourceCompiler()));
  }

  public @NonNull ProcessCodeGenerator processCodeGenerator() {
    return singleton(() -> new ProcessCodeGenerator(codegenNaming()));
  }

  public @NonNull TransactionCodeGenerator transactionCodeGenerator() {
    return singleton(() -> new TransactionCodeGenerator(codegenNaming()));
  }

  public @NonNull GeneratedClassProviderGenerator generatedClassProviderGenerator() {
    return singleton(() -> new GeneratedClassProviderGenerator(codegenNaming()));
  }

  public @NonNull DefinitionProviderGenerator definitionProviderGenerator() {
    return singleton(() -> new DefinitionProviderGenerator(logLevel));
  }

  public @NonNull CodeWriter codeWriter() {
    return singleton(CodeWriter::new);
  }

  public @NonNull DescriptorFactory descriptorFactory() {
    return singleton(DescriptorFactory::new);
  }

  public @NonNull SemanticValidator semanticValidator() {
    return singleton(SemanticValidator::new);
  }

  public @NonNull HelperRegistry helperRegistry() {
    return singleton(DefaultHelperRegistry::new);
  }

  public @NonNull DslCompiler dslCompiler() {
    return singleton(
            () -> new DslCompiler(
                    dslSourceCompiler(),
                    processCodeGenerator(),
                    transactionCodeGenerator(),
                    generatedClassProviderGenerator(),
                    codeWriter(),
                    descriptorFactory(),
                    semanticValidator(),
                    helperRegistry(),
                    logLevel));
  }

  /* ============= Holder ============= */

  @Getter
  private static final class Holder implements SingletonSupport {

    public static final Holder INSTANCE = new Holder();

    private final SingletonScope scope = SingletonScope.of();

    public CompileConfig compileConfig() {
      return compileConfig(SingletonScope.of(), Level.INFO);
    }

    public CompileConfig compileConfig(Scope scope, Level logLevel) {
      return singleton(scope.id(), () -> new CompileConfig(scope, logLevel));
    }
  }
}

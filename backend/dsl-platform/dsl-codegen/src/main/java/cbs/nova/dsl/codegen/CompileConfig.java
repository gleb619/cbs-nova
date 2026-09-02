package cbs.nova.dsl.codegen;

import cbs.nova.dsl.codegen.SemanticValidator;
import cbs.nova.dsl.codegen.generator.DefinitionProviderGenerator;
import cbs.nova.dsl.codegen.generator.GeneratedClassProviderGenerator;
import cbs.nova.dsl.codegen.generator.ModelRegistryGenerator;
import cbs.nova.dsl.codegen.generator.ProcessCodeGenerator;
import cbs.nova.dsl.codegen.generator.TransactionCodeGenerator;
import cbs.nova.dsl.codegen.model.CodegenNaming;
import cbs.nova.dsl.codegen.util.AstExtractor;
import cbs.nova.dsl.codegen.util.DslPackageNameResolver;
import cbs.nova.dsl.codegen.util.Json;
import cbs.nova.dsl.codegen.util.ModelTypeExtractor;
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

  public static CompileConfig compileConfig() {
    return Holder.INSTANCE.compileConfig();
  }

  public static CompileConfig compileConfig(Scope scope) {
    return Holder.INSTANCE.compileConfig(scope);
  }

  /* ============= */

  public @NonNull CodegenNaming codegenNaming() {
    return singleton(CodegenNaming::new);
  }

  public @NonNull SourceCompiler sourceCompiler() {
    return singleton(() -> new SourceCompiler(
            definitionProviderGenerator(), codeWriter(), codegenNaming()));
  }

  public @NonNull DslSourceCompiler dslSourceCompiler() {
    return singleton(() -> new DslSourceCompiler(sourceCompiler()));
  }

  public @NonNull ProcessCodeGenerator processCodeGenerator() {
    return singleton(() -> {
      var codegenNaming = codegenNaming();
      return new ProcessCodeGenerator(new DslPackageNameResolver(codegenNaming));
    });
  }

  public @NonNull TransactionCodeGenerator transactionCodeGenerator() {
    return singleton(() -> {
      var codegenNaming = codegenNaming();
      return new TransactionCodeGenerator(new DslPackageNameResolver(codegenNaming));
    });
  }

  public @NonNull Json json() {
    return singleton(Json::new);
  }

  public @NonNull AstExtractor executeAstJsonExtractor() {
    return singleton(() -> new AstExtractor(json()));
  }

  public @NonNull GeneratedClassProviderGenerator generatedClassProviderGenerator() {
    return singleton(
            () -> new GeneratedClassProviderGenerator(executeAstJsonExtractor(), new DslPackageNameResolver(codegenNaming())));
  }

  public @NonNull ModelTypeExtractor modelTypeExtractor() {
    return singleton(ModelTypeExtractor::new);
  }

  public @NonNull ModelRegistryGenerator modelRegistryGenerator() {
    return singleton(
            () -> new ModelRegistryGenerator(codeWriter(), codegenNaming(), modelTypeExtractor()));
  }

  public @NonNull DefinitionProviderGenerator definitionProviderGenerator() {
    return singleton(() -> new DefinitionProviderGenerator(codeWriter()));
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
                    modelRegistryGenerator(),
                    dslSourceCompiler(),
                    processCodeGenerator(),
                    transactionCodeGenerator(),
                    generatedClassProviderGenerator(),
                    codeWriter(),
                    descriptorFactory(),
                    semanticValidator(),
                    helperRegistry(),
                    codegenNaming()));
  }

  /* ============= */

  @Getter
  private static final class Holder implements SingletonSupport {

    public static final Holder INSTANCE = new Holder();

    private final SingletonScope scope = SingletonScope.of();

    public CompileConfig compileConfig() {
      return compileConfig(SingletonScope.of());
    }

    public CompileConfig compileConfig(Scope scope) {
      return singleton(scope.id(), () -> new CompileConfig(scope));
    }
  }
}

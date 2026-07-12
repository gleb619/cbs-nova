package cbs.nova.dsl.codegen;

import cbs.nova.dsl.config.SingletonSupport;
import cbs.nova.dsl.config.SingletonSupport.Replaceable;
import cbs.nova.dsl.config.SingletonSupport.Scope;
import cbs.nova.dsl.config.SingletonSupport.SingletonScope;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

@Getter
public final class CompileConfig implements SingletonSupport {

  private final Scope scope;

  public CompileConfig(Scope scope) {
    this.scope = scope;
  }

  public static CompileConfig compileConfig() {
    return Holder.INSTANCE.compileConfig();
  }

  public static CompileConfig compileConfig(Scope scope) {
    return Holder.INSTANCE.compileConfig(scope);
  }

  /* ============= Bean definitions ============= */

  public @NonNull SourceCompiler sourceCompiler() {
    return singleton(SourceCompiler::new);
  }

  public @NonNull DslSourceCompiler dslSourceCompiler() {
    return singleton(DslSourceCompiler::new);
  }

  public @NonNull ProcessCodeGenerator processCodeGenerator() {
    return singleton(ProcessCodeGenerator::new);
  }

  public @NonNull TransactionCodeGenerator transactionCodeGenerator() {
    return singleton(TransactionCodeGenerator::new);
  }

  public @NonNull GeneratedClassProviderGenerator generatedClassProviderGenerator() {
    return singleton(GeneratedClassProviderGenerator::new);
  }

  public @NonNull DefinitionProviderGenerator definitionProviderGenerator() {
    return singleton(DefinitionProviderGenerator::new);
  }

  public @NonNull CodeWriter codeWriter() {
    return singleton(CodeWriter::new);
  }

  public @NonNull DslCompiler dslCompiler() {
    return singleton(DslCompiler::new);
  }

  /**
   * Mutable log level shared across compilation components.
   * <p>
   * Defaults to {@link Level#INFO}. Callers may replace the level at runtime
   * without recreating any beans.
   */
  public @NonNull Replaceable<Level> logLevel() {
    return singleton(() -> Replaceable.of(() -> Level.INFO));
  }

  /* ============= Holder ============= */

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
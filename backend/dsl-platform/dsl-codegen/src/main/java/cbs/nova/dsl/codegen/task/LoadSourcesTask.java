package cbs.nova.dsl.codegen.task;

import cbs.nova.dsl.codegen.DslSourceCompiler;
import cbs.nova.dsl.codegen.SourceCompiler;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

@RequiredArgsConstructor
public final class LoadSourcesTask implements CompileTask {

  private final DslSourceCompiler dslSourceCompiler;

  @Override
  public String name() {
    return "load";
  }

  @Override
  public CompileContext run(CompileContext context) throws IOException {
    var options = context.options();
    var sourceOptions = new SourceCompiler.CompileOptions(
            options.buildVersion(),
            options.targetPackage(),
            options.logLevel(),
            options.classpath(),
            options.useFileNameSubPackage());
    return context.toBuilder()
            .objects(dslSourceCompiler.compileAndLoad(
                    options.srcDir(), options.outputDir(), sourceOptions))
            .build();
  }
}

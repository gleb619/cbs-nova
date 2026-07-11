package cbs.nova.dsl.codegen;

import lombok.extern.slf4j.Slf4j;

import javax.tools.ToolProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

@Slf4j
public final class DslCompiler {

  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      log.error("Usage: DslCompiler <srcDir> <outputDir>");
      System.exit(1);
    }
    compile(Path.of(args[0]), Path.of(args[1]));
  }

  public static void compile(Path srcDir, Path outputDir) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }

    var descriptors = new SourceCompiler().compileAndDescribe(srcDir, outputDir, compiler);

    var processGen = new ProcessCodeGenerator();
    var txGen = new TransactionCodeGenerator();
    var sources = new ArrayList<GeneratedSource>();
    for (var p : descriptors.processes()) {
      sources.addAll(processGen.generate(p));
    }
    for (var t : descriptors.transactions()) {
      sources.addAll(txGen.generate(t));
    }

    CodeWriter.write(sources, outputDir);
    log.info("[DslCompiler] Generated {} source(s) to {}", sources.size(), outputDir);
  }
}

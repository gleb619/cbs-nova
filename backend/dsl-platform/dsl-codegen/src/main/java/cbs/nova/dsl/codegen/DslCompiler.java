package cbs.nova.dsl.codegen;

import cbs.nova.dsl.codegen.model.DslCompilerOptions;
import cbs.nova.dsl.codegen.task.CompileContext;
import cbs.nova.dsl.codegen.task.CompileTask;
import cbs.nova.dsl.codegen.task.StepTiming;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.event.Level;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public final class DslCompiler {

  private final CompileConfig config;
  private final String defaultBasePackage;

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      log.atLevel(Level.ERROR).log(
              () -> "Usage: DslCompiler <serialized java.util.Properties>");
      System.exit(1);
    }
    var options = DslCompilerOptions.fromProperties(args[0]);
    compile(options);
  }

  public static void compile(@NonNull DslCompilerOptions options) throws IOException {
    var defaultBasePackage = options.basePackage() != null && !options.basePackage().isBlank()
            ? options.basePackage()
            : CompileConfig.DEFAULT_GENERATED_BASE_PACKAGE;
    CompileConfig.compileConfig()
            .dslCompiler(defaultBasePackage)
            .compileInternal(options);
  }

  private void compileInternal(@NonNull DslCompilerOptions options) throws IOException {
    var context = new AtomicReference<>(CompileContext.create(options));
    List<CompileTask> tasks = config.compileTasks(defaultBasePackage);

    var timings = new ArrayList<StepTiming>();
    for (var task : tasks) {
      var start = Instant.now();
      context.set(runTask(task, context.get()));
      timings.add(new StepTiming(task.name(), Duration.between(start, Instant.now())));
    }

    logSummary(timings, context.get().generatedSources().size(), options.outputDir());
  }

  private CompileContext runTask(CompileTask task, CompileContext context) throws IOException {
    try {
      return task.run(context);
    } catch (IOException | RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Compilation task '%s' failed".formatted(task.name()), e);
    }
  }

  private void logSummary(List<StepTiming> timings, int sourceCount, Path outputDir) {
    var total = timings.stream()
            .map(StepTiming::duration)
            .reduce(Duration.ZERO, Duration::plus);
    log.atLevel(Level.INFO).log(() -> "[DslCompiler] Generated %s source(s) to %s in %s"
            .formatted(sourceCount, outputDir, humanReadable(total)));
    String report = timings.stream()
            .map(t -> "  %s - %s".formatted(t.phase(), humanReadable(t.duration())))
            .collect(Collectors.joining("\n"));
    log.atLevel(Level.DEBUG).log(() -> "[DslCompiler] Generation report: \n%s"
            .formatted(report));
  }

  private String humanReadable(Duration duration) {
    var millis = duration.toMillis();
    return millis < 1000 ? millis + " ms" : "%.2f s".formatted(millis / 1000.0);
  }
}

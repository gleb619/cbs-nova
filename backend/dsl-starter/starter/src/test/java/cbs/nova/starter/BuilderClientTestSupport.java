package cbs.nova.starter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.model.CompileModels.CompileRequest;
import cbs.nova.starter.model.CompileModels.CompileResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Test stand-ins for the remote dsl-builder service. {@link #stubLocalCompile(DslBuilderClient)}
 * compiles the submitted sources in-test with the system Java compiler (test-only replacement for
 * the removed in-process production fallback, T570) until T601 provides a dsl-builder
 * testcontainer.
 */
public final class BuilderClientTestSupport {

  private BuilderClientTestSupport() {
  }

  @SuppressWarnings("unchecked")
  public static ObjectProvider<DslBuilderClient> providerOf(DslBuilderClient client) {
    ObjectProvider<DslBuilderClient> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(client);
    return provider;
  }

  static void stubSuccessfulCompile(DslBuilderClient client) {
    when(client.compile(any(CompileRequest.class)))
            .thenReturn(new CompileResult("s-1", true, List.of(), List.of(), 1));
    when(client.downloadZip("s-1")).thenReturn(emptyZip());
  }

  /**
   * Stubs {@code compile}/{@code downloadZip} to compile the request sources with the system Java
   * compiler and return the compiled classes as the zip payload — a local fake of the dsl-builder
   * service. On compile failure returns {@code success=false} with javac diagnostics as message
   * strings (mirroring the remote contract, where diagnostics arrive as plain strings).
   */
  public static void stubLocalCompile(DslBuilderClient client) {
    Map<String, byte[]> zips = new ConcurrentHashMap<>();
    AtomicLong sequence = new AtomicLong();
    when(client.compile(any(CompileRequest.class))).thenAnswer(invocation -> {
      CompileRequest request = invocation.getArgument(0);
      return compileLocally(request, zips, sequence.incrementAndGet());
    });
    when(client.downloadZip(anyString())).thenAnswer(invocation -> {
      byte[] zip = zips.get(invocation.getArgument(0));
      if (zip == null) {
        throw new IllegalStateException("no compiled zip for id " + invocation.getArgument(0));
      }
      return zip;
    });
  }

  private static CompileResult compileLocally(CompileRequest request, Map<String, byte[]> zips,
          long sequence) throws IOException {
    String id = "local-" + sequence;
    long started = System.nanoTime();
    Map<String, String> sources = request.sources() == null ? Map.of() : request.sources();
    if (sources.isEmpty()) {
      zips.put(id, emptyZip());
      return new CompileResult(id, true, List.of(), List.of(), elapsedMillis(started));
    }
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("No system Java compiler available (JDK required)");
    }
    Path tempDir = Files.createTempDirectory("local-builder-");
    Path sourceDir = tempDir.resolve("src");
    Path outputDir = tempDir.resolve("out");
    Files.createDirectories(sourceDir);
    Files.createDirectories(outputDir);
    try {
      List<Path> javaFiles = new ArrayList<>();
      for (Map.Entry<String, String> source : sources.entrySet()) {
        Path file = sourceDir.resolve(source.getKey()).normalize();
        if (!file.startsWith(sourceDir)) {
          continue;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, source.getValue());
        javaFiles.add(file);
      }
      var classpath = System.getProperty("java.class.path");
      var messages = new ArrayList<String>();
      try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null)) {
        var options = List.of("-classpath", classpath, "-d", outputDir.toString());
        for (var file : javaFiles) {
          var diagnostics = new DiagnosticCollector<JavaFileObject>();
          var unit = fm.getJavaFileObjectsFromFiles(List.of(file.toFile()));
          var task = compiler.getTask(null, fm, diagnostics, options, null, unit);
          if (!task.call()) {
            for (var d : diagnostics.getDiagnostics()) {
              messages.add(toMessage(d));
            }
          }
        }
      }
      if (!messages.isEmpty()) {
        return new CompileResult(id, false, List.of(), messages, elapsedMillis(started));
      }
      List<String> generated = new ArrayList<>();
      byte[] zip;
      try (ByteArrayOutputStream out = new ByteArrayOutputStream();
              ZipOutputStream zipOut = new ZipOutputStream(out);
              Stream<Path> stream = Files.walk(outputDir)) {
        for (Path file : stream.filter(Files::isRegularFile).toList()) {
          String name = outputDir.relativize(file).toString().replace('\\', '/');
          generated.add(name);
          zipOut.putNextEntry(new ZipEntry(name));
          zipOut.write(Files.readAllBytes(file));
          zipOut.closeEntry();
        }
        zipOut.finish();
        zip = out.toByteArray();
      }
      zips.put(id, zip);
      return new CompileResult(id, true, generated, List.of(), elapsedMillis(started));
    } finally {
      try (Stream<Path> stream = Files.walk(tempDir)) {
        for (Path file : stream.sorted((a, b) -> -a.compareTo(b)).toList()) {
          Files.deleteIfExists(file);
        }
      }
    }
  }

  private static String toMessage(Diagnostic<? extends JavaFileObject> d) {
    var source = d.getSource();
    var name = source == null ? "<unknown>" : source.getName();
    var line = d.getLineNumber() == Diagnostic.NOPOS ? null : d.getLineNumber();
    return line == null
            ? name + ": " + d.getMessage(Locale.getDefault())
            : name + ":" + line + ": " + d.getMessage(Locale.getDefault());
  }

  private static long elapsedMillis(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000;
  }

  static byte[] emptyZip() {
    var out = new ByteArrayOutputStream();
    try (var ignored = new ZipOutputStream(out)) {
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

}

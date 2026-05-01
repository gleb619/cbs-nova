package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class RegistrationGeneratorTest {

  static class FakeFiler implements Filer {
    final Map<String, TestWriter> files = new HashMap<>();

    @Override
    public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements)
        throws IOException {
      String nameStr = name.toString();
      TestWriter writer = new TestWriter(nameStr);
      files.put(nameStr, writer);
      return writer;
    }

    @Override
    public JavaFileObject createClassFile(CharSequence name, Element... originatingElements)
        throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileObject createResource(
        JavaFileManager.Location location,
        CharSequence pkg,
        CharSequence relativeName,
        Element... originatingElements)
        throws IOException {
      String key =
          location.toString() + "/" + (pkg.toString().isEmpty() ? "" : pkg + "/") + relativeName;
      TestWriter writer = new TestWriter(key);
      files.put(key, writer);
      return writer;
    }

    @Override
    public FileObject getResource(
        JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName)
        throws IOException {
      String key =
          location.toString() + "/" + (pkg.toString().isEmpty() ? "" : pkg + "/") + relativeName;
      if (files.containsKey(key)) {
        return files.get(key);
      }
      throw new IOException("Resource not found: " + key);
    }
  }

  static class TestWriter extends SimpleJavaFileObject implements JavaFileObject, FileObject {
    final StringWriterWithClose writer = new StringWriterWithClose();

    TestWriter(String name) {
      super(URI.create("mem:///" + name), JavaFileObject.Kind.OTHER);
    }

    @Override
    public Writer openWriter() {
      return writer;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
      return writer.getContent();
    }

    public String getContent() {
      return writer.getContent();
    }
  }

  static class StringWriterWithClose extends Writer {
    final StringBuilder sb = new StringBuilder();
    boolean closed = false;

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
      if (closed) throw new IOException("Stream closed");
      sb.append(cbuf, off, len);
    }

    @Override
    public void flush() throws IOException {
      if (closed) throw new IOException("Stream closed");
    }

    @Override
    public void close() throws IOException {
      closed = true;
    }

    public String getContent() {
      return sb.toString();
    }
  }

  @Test
  @DisplayName("shouldGenerateImplRegistrationsClassWithRegisterCalls")
  void shouldGenerateImplRegistrationsClassWithRegisterCalls() throws Exception {
    FakeFiler filer = new FakeFiler();
    List<RegistrationSpec> specs = List.of(
        new RegistrationSpec(
            "com.example",
            "TxOne",
            "TX_1",
            DslInterfaceType.TRANSACTION,
            "cbs.dsl.api.TransactionTypes.TransactionInput",
            "cbs.dsl.api.TransactionTypes.TransactionOutput"),
        new RegistrationSpec(
            "com.example",
            "HelperOne",
            "H_1",
            DslInterfaceType.HELPER,
            "cbs.dsl.api.HelperTypes.HelperInput",
            "cbs.dsl.api.HelperTypes.HelperOutput"));

    new RegistrationGenerator(filer).generate(specs);

    // Print generated files for debugging
    System.out.println("Generated files: " + filer.files.keySet());
    for (var entry : filer.files.entrySet()) {
      System.out.println("  " + entry.getKey() + " -> "
          + entry
              .getValue()
              .getContent()
              .substring(0, Math.min(40, entry.getValue().getContent().length())));
    }

    // Check that the generated class exists
    assertTrue(
        filer.files.entrySet().stream()
            .anyMatch(e -> e.getKey().contains("GeneratedImplRegistrations")),
        "Should contain GeneratedImplRegistrations class");

    // Find and verify the generated class content
    String generatedClassKey = filer.files.keySet().stream()
        .filter(k -> k.contains("GeneratedImplRegistrations"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No GeneratedImplRegistrations found"));

    String content = filer.files.get(generatedClassKey).getContent();
    assertTrue(
        content.contains("class GeneratedImplRegistrations implements ImplRegistrationProvider"),
        "Content should contain class declaration: " + content);
    assertTrue(
        content.contains("registry.register(new TxOneDefinition())"),
        "Content should register TxOneDefinition: " + content);
    assertTrue(
        content.contains("registry.register(new HelperOneDefinition())"),
        "Content should register HelperOneDefinition: " + content);
  }

  @Test
  @DisplayName("shouldGenerateSpiServiceFile")
  void shouldGenerateSpiServiceFile() throws Exception {
    FakeFiler filer = new FakeFiler();

    new RegistrationGenerator(filer).generate(List.of());

    // Print generated files for debugging
    System.out.println("Generated SPI files: " + filer.files.keySet());
    for (var entry : filer.files.entrySet()) {
      System.out.println("  " + entry.getKey() + " -> " + entry.getValue().getContent());
    }

    assertTrue(
        filer.files.entrySet().stream().anyMatch(e -> e.getKey()
            .contains("META-INF/services/cbs.dsl.api.ImplRegistrationProvider")),
        "Should contain SPI service file");

    String spiKey = filer.files.keySet().stream()
        .filter(k -> k.contains("META-INF/services/cbs.dsl.api.ImplRegistrationProvider"))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No SPI file found"));

    String spiContent = filer.files.get(spiKey).getContent();
    assertTrue(
        spiContent.contains("cbs.dsl.codegen.generated.GeneratedImplRegistrations"),
        "SPI file should contain GeneratedImplRegistrations class name: " + spiContent);
  }
}

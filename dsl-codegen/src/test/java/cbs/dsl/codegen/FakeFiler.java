package cbs.dsl.codegen;

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
import java.util.Map;

class FakeFiler implements Filer {
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
}

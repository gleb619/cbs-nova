package cbs.dsl.codegen;

import cbs.dsl.codegen.DslCompiler.FileWrite;

import java.io.IOException;
import java.util.List;

public interface DefinitionGenerator {

  List<FileWrite> generate(List<RegistrationModel> specs) throws IOException;

  void write(List<FileWrite> files) throws IOException;
}

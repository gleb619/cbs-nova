package cbs.dsl.api;

import java.util.List;

/**
 * A DSL compilation unit that returns all {@link DslObject} instances it produced. The DslCompiler
 * wraps each implicit-class DSL file with a class that implements this interface, and the instances
 * are collected locally inside that class rather than via a global static collector.
 */
@FunctionalInterface
public interface DslCompilationUnit {
  List<DslObject> getDslObjects();
}

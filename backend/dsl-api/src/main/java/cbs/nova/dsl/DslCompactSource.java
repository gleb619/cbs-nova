package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

import java.util.List;

public interface DslCompactSource {

  @NonNull
  List<DslObject> define();
}

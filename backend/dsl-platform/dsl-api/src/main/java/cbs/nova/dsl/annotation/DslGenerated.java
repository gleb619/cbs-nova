package cbs.nova.dsl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface DslGenerated {

  String generator();

  String timestamp();

  String javaVersion();

  String user();

  // TODO: add here two new fields: `dslBuildInfo`, `dslGitInfo`, so we can know with what version
  // of cbs codegen class was created

}

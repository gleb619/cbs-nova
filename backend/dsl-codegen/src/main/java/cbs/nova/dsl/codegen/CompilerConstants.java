package cbs.nova.dsl.codegen;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CompilerConstants {

  public static final String DSL_FOLDER = "dsl";
  public static final String MODELS_FOLDER = "models";

  public static final String PROCESS_BUILDER_NAME = "process";
  public static final String TRANSACTION_BUILDER_NAME = "transaction";
  public static final String EXECUTE_METHOD_NAME = "execute";

}

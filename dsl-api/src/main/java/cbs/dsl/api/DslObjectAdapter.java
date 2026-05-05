package cbs.dsl.api;

import cbs.dsl.api.EventTypes.EventOutput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.MassOperationTypes.MassOperationOutput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Adapts a runtime {@link DslObject} (produced by a builder) into a {@link DslDefinition} proxy so
 * it can be registered in a {@link WritableRegistry}.
 *
 * <p>This bridge is used only in {@code REFLECTED} dev mode. Production code always works with
 * generated {@code *Definition} implementations.
 */
public final class DslObjectAdapter {

  private DslObjectAdapter() {}

  @SuppressWarnings("unchecked")
  public static <T extends DslDefinition> T adapt(DslObject object, Class<T> definitionType) {
    ClassLoader cl = DslObjectAdapter.class.getClassLoader();
    return (T) Proxy.newProxyInstance(cl, new Class<?>[] {definitionType}, new Handler(object));
  }

  private static class Handler implements InvocationHandler {

    private final DslObject object;

    Handler(DslObject object) {
      this.object = object;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String name = method.getName();

      // getCode() is the only method guaranteed on DslObject
      if ("getCode".equals(name) && method.getParameterCount() == 0) {
        return object.getCode();
      }

      // Try to find the method on the DslObject's concrete class
      try {
        Method implMethod = object.getClass().getDeclaredMethod(name, method.getParameterTypes());
        return implMethod.invoke(object, args);
      } catch (NoSuchMethodException e) {
        // Return defaults for common DslDefinition methods
        return defaultValue(method);
      }
    }

    private static Object defaultValue(Method method) {
      Class<?> returnType = method.getReturnType();
      if (returnType == String.class) {
        return "";
      }
      if (returnType == List.class) {
        return Collections.emptyList();
      }
      if (returnType == EventOutput.class) {
        return new EventOutput(Collections.emptyMap());
      }
      if (returnType == TransactionOutput.class) {
        return TransactionOutput.empty();
      }
      if (returnType == WorkflowOutput.class) {
        return new WorkflowOutput("DONE");
      }
      if (returnType == HelperOutput.class) {
        return new HelperOutput(null);
      }
      if (returnType == MassOperationOutput.class) {
        return new MassOperationOutput(0L, 0L, "SUCCESS");
      }
      if (returnType == ConditionTypes.ConditionOutput.class) {
        return new ConditionTypes.ConditionOutput(false);
      }
      if (returnType == Consumer.class
          || returnType == BiConsumer.class
          || returnType == Predicate.class) {
        return null;
      }
      if (returnType == boolean.class) {
        return false;
      }
      if (returnType == int.class) {
        return 0;
      }
      if (returnType == long.class) {
        return 0L;
      }
      if (returnType.isPrimitive()) {
        return 0;
      }
      return null;
    }
  }
}

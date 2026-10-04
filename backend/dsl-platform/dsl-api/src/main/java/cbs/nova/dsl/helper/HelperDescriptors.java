package cbs.nova.dsl.helper;

import static cbs.nova.dsl.config.Constants.EMPTY_MARKDOWN;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.annotation.Helper;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Derives an {@link ExecutableDescriptor} for helpers that do not override
 * {@link Executable#describe()}.
 *
 * <p>
 * Most helpers only implement {@code execute(...)}; the inherited default descriptor is
 * {@link ExecutableDescriptor#empty()}, so introspection endpoints reported {@code null} for
 * description/input/output and the generated helpers catalog rendered blank columns. This factory
 * completes those fields from three sources:
 *
 * <ul>
 * <li><b>Types</b> — the {@code IN}/{@code OUT} type arguments the helper class binds to
 * {@link Executable}.</li>
 * <li><b>Description</b> — the {@link Helper#description()} annotation attribute, falling back to a
 * meta-annotation carrying {@link Helper} (such as Spring-specific stereotype annotations) and
 * finally to {@link Executable#description()} when the override is not the {@code <!-- NONE -->}
 * placeholder.</li>
 * </ul>
 *
 * <p>
 * Explicitly declared descriptor values always win: only {@code null} fields are substituted, so
 * helpers overriding {@code describe()} keep full control.
 */
public final class HelperDescriptors {

  /** Guards against pathological generic hierarchies. */
  private static final int MAX_TYPE_DEPTH = 16;

  private HelperDescriptors() {
  }

  /**
   * Returns the effective descriptor for the given helper, completing missing fields from the
   * helper's generic binding and its {@link Helper} annotation.
   *
   * @param helper
   *          the registered helper instance
   * @return descriptor with input/output types and description resolved where possible
   */
  public static @NonNull ExecutableDescriptor describe(@NonNull Executable<?, ?> helper) {
    var declared = helper.describe();
    var annotation = helper.getClass().getAnnotation(Helper.class);
    var types = resolve(helper.getClass());
    return new ExecutableDescriptor(
            declared.name(),
            descriptionOf(declared, annotation, helper),
            declared.inputType() != null ? declared.inputType() : types[0],
            declared.outputType() != null ? declared.outputType() : types[1],
            declared.parameters() == null ? List.of() : declared.parameters());
  }

  private static @Nullable String descriptionOf(
          @NonNull ExecutableDescriptor declared,
          @Nullable Helper annotation,
          @NonNull Executable<?, ?> helper) {
    if (declared.description() != null && !declared.description().isBlank()) {
      return declared.description();
    }
    if (annotation != null && !annotation.description().isBlank()) {
      return annotation.description();
    }
    var meta = descriptionFromMetaAnnotation(helper);
    if (meta != null && !meta.isBlank()) {
      return meta;
    }
    return markdown(helper.description()).orElse(null);
  }

  /**
   * Looks for an annotation on the helper class that is itself meta-annotated with {@link Helper}
   * (e.g. framework-specific stereotype annotations) and returns its {@code description()} value if
   * present and non-blank.
   */
  private static @Nullable String descriptionFromMetaAnnotation(@NonNull Executable<?, ?> helper) {
    for (var annotation : helper.getClass().getAnnotations()) {
      if (annotation.annotationType().getAnnotation(Helper.class) == null) {
        continue;
      }
      try {
        var method = annotation.annotationType().getMethod("description");
        var value = method.invoke(annotation);
        if (value instanceof String s && !s.isBlank()) {
          return s;
        }
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
        // Ignore: meta-annotation does not expose a description attribute.
      }
    }
    return null;
  }

  private static @NonNull Optional<String> markdown(@Nullable String value) {
    if (value == null || value.isBlank() || EMPTY_MARKDOWN.equals(value)) {
      return Optional.empty();
    }
    return Optional.of(value);
  }

  private static @NonNull Class<?>[] resolve(@NonNull Type type) {
    var resolved = resolve(type, Map.of(), 0);
    return resolved != null ? resolved : new Class<?>[]{null, null};
  }

  private static @Nullable Class<?>[] resolve(
          @NonNull Type type,
          @NonNull Map<String, Type> bindings,
          int depth) {
    if (depth > MAX_TYPE_DEPTH) {
      return null;
    }
    if (type instanceof ParameterizedType parameterized) {
      var raw = (Class<?>) parameterized.getRawType();
      var arguments = parameterized.getActualTypeArguments();
      var parameters = raw.getTypeParameters();
      var next = new HashMap<>(bindings);
      for (int i = 0; i < parameters.length && i < arguments.length; i++) {
        next.put(parameters[i].getName(), arguments[i]);
      }
      if (Executable.class.equals(raw)) {
        return new Class<?>[]{
            toClass(arguments[0], next, depth + 1),
            toClass(arguments[1], next, depth + 1)};
      }
      var fromSuper = resolveSuper(raw, next, depth + 1);
      if (fromSuper != null) {
        return fromSuper;
      }
    } else if (type instanceof Class<?> raw) {
      if (Executable.class.equals(raw)) {
        return new Class<?>[]{null, null};
      }
      var fromSuper = resolveSuper(raw, bindings, depth + 1);
      if (fromSuper != null) {
        return fromSuper;
      }
    }
    return null;
  }

  private static @Nullable Class<?>[] resolveSuper(
          @NonNull Class<?> raw,
          @NonNull Map<String, Type> bindings,
          int depth) {
    if (depth > MAX_TYPE_DEPTH) {
      return null;
    }
    for (var interfaceType : raw.getGenericInterfaces()) {
      var nested = resolve(interfaceType, bindings, depth);
      if (nested != null) {
        return nested;
      }
    }
    var superType = raw.getGenericSuperclass();
    if (superType == null || Object.class.equals(superType)) {
      return null;
    }
    return resolve(superType, bindings, depth);
  }

  private static @Nullable Class<?> toClass(
          @Nullable Type type,
          @NonNull Map<String, Type> bindings,
          int depth) {
    if (type == null || depth > MAX_TYPE_DEPTH) {
      return null;
    }
    if (type instanceof Class<?> clazz) {
      return clazz;
    }
    if (type instanceof ParameterizedType parameterized) {
      return (Class<?>) parameterized.getRawType();
    }
    if (type instanceof TypeVariable<?> variable) {
      var bound = bindings.get(variable.getName());
      return bound == null ? null : toClass(bound, bindings, depth + 1);
    }
    if (type instanceof GenericArrayType array) {
      var component = toClass(array.getGenericComponentType(), bindings, depth + 1);
      return component == null
              ? null
              : java.lang.reflect.Array.newInstance(component, 0).getClass();
    }
    if (type instanceof WildcardType wildcard) {
      var bounds = wildcard.getUpperBounds();
      return bounds.length > 0 ? toClass(bounds[0], bindings, depth + 1) : null;
    }
    return null;
  }
}

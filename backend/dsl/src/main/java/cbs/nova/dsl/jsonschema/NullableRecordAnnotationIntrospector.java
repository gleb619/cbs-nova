package cbs.nova.dsl.jsonschema;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedMember;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Marks record components as required unless they carry a jspecify {@link Nullable} type
 * annotation, so {@code BeanPropertyWriter.depositSchemaProperty} routes them through
 * {@code property(...)} instead of {@code optionalProperty(...)}.
 *
 * <p>
 * jspecify {@code @Nullable} is a TYPE_USE-only annotation: javac propagates it from the record
 * component to the annotated type of the field, accessor and constructor parameter, but not to
 * their declaration annotations. It is therefore read from the member's annotated type via the
 * Jackson introspected member, replacing the former raw {@code java.lang.reflect.Field} check.
 * </p>
 */
final class NullableRecordAnnotationIntrospector extends JacksonAnnotationIntrospector {

  @Override
  public Boolean hasRequiredMarker(MapperConfig<?> config, AnnotatedMember member) {
    Boolean standard = super.hasRequiredMarker(config, member);
    if (Boolean.TRUE.equals(standard)) {
      return Boolean.TRUE;
    }
    Class<?> declaringClass = member.getDeclaringClass();
    if (declaringClass == null || !declaringClass.isRecord()) {
      return standard;
    }
    return isNullable(member) ? Boolean.FALSE : Boolean.TRUE;
  }

  private static boolean isNullable(AnnotatedMember member) {
    AnnotatedElement element = member.getAnnotated();
    if (element == null) {
      return false;
    }
    AnnotatedType annotatedType = annotatedTypeOf(element);
    return annotatedType != null && annotatedType.isAnnotationPresent(Nullable.class);
  }

  private static AnnotatedType annotatedTypeOf(AnnotatedElement element) {
    if (element instanceof Field field) {
      return field.getAnnotatedType();
    }
    if (element instanceof Method method) {
      return method.getAnnotatedReturnType();
    }
    if (element instanceof Parameter parameter) {
      return parameter.getAnnotatedType();
    }
    return null;
  }
}

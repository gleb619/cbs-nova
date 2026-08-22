package cbs.nova.starter.annotation;

import cbs.nova.dsl.annotation.Helper;
import cbs.nova.dsl.annotation.Helper.ComponentModel;
import cbs.nova.dsl.annotation.Helper.CreationStrategy;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Component
@Helper(name = "#name", componentModel = ComponentModel.LAZY, creationStrategy = CreationStrategy.STANDARD)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SpringHelper {

  String name();

}

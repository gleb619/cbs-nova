package cbs.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.mapstruct.Mapper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "cbs.app")
public class MainConventions {

  private static final String ROOT = "cbs.app";

  // Naming Conventions

  @ArchTest
  static final ArchRule CONTROLLERS_SHOULD_BE_SUFFIXED = classes()
      .that()
      .resideInAPackage(ROOT + ".controller..")
      .and()
      .areNotNestedClasses()
      .and()
      .haveSimpleNameNotEndingWith("Test")
      .should()
      .haveSimpleNameEndingWith("Controller")
      .orShould()
      .haveSimpleNameEndingWith("ExceptionHandler")
      .allowEmptyShould(true)
      .because(
          "All classes in 'controller' package must end with 'Controller' or 'ExceptionHandler'");

  @ArchTest
  static final ArchRule SERVICES_SHOULD_BE_SUFFIXED = classes()
      .that()
      .resideInAPackage(ROOT + ".service..")
      .should()
      .haveSimpleNameEndingWith("Service")
      .orShould()
      .haveSimpleNameEndingWith("ServiceImpl")
      .allowEmptyShould(true)
      .because("All classes in 'service' package must end with 'Service' or 'ServiceImpl'");

  @ArchTest
  static final ArchRule REPOSITORIES_SHOULD_BE_SUFFIXED = classes()
      .that()
      .resideInAPackage(ROOT + ".repository..")
      .should()
      .haveSimpleNameEndingWith("Repository")
      .allowEmptyShould(true)
      .because("All classes in 'repository' package must end with 'Repository'");

  @ArchTest
  static final ArchRule ENTITIES_SHOULD_BE_SUFFIXED = classes()
      .that()
      .resideInAPackage(ROOT + ".entity..")
      .and()
      .areNotNestedClasses()
      .should()
      .haveSimpleNameEndingWith("Entity")
      .allowEmptyShould(true)
      .because("All classes in 'entity' package must end with 'Entity'");

  @ArchTest
  static final ArchRule MAPPERS_SHOULD_BE_SUFFIXED = classes()
      .that()
      .resideInAPackage(ROOT + ".mapper..")
      .should()
      .haveSimpleNameEndingWith("Mapper")
      .orShould()
      .haveSimpleNameEndingWith("MapperImpl")
      .allowEmptyShould(true)
      .because(
          "All classes in 'mapper' package must end with 'Mapper' or 'MapperImpl' (MapStruct-generated)");

  @ArchTest
  static final ArchRule DTOS_SHOULD_BE_SUFFIXED = classes()
      .that()
      .resideInAPackage(ROOT + ".dto..")
      .or()
      .resideInAPackage(ROOT + ".model..")
      .and()
      .haveSimpleNameNotEndingWith("OpenApi")
      .and()
      .areNotNestedClasses()
      .and()
      .areNotAssignableTo(Throwable.class)
      .should()
      .haveSimpleNameEndingWith("Dto")
      .orShould()
      .haveSimpleNameEndingWith("Request")
      .orShould()
      .haveSimpleNameEndingWith("Response")
      .allowEmptyShould(true)
      .because("DTOs must end with 'Dto', 'Request', or 'Response'");

  // Annotation → Package Enforcement

  @ArchTest
  static final ArchRule CONTROLLER_ANNOTATION_ONLY_IN_CONTROLLER_PACKAGE = classes()
      .that()
      .areAnnotatedWith(RestController.class)
      .or()
      .areAnnotatedWith(Controller.class)
      .should()
      .resideInAPackage(ROOT + ".controller..")
      .allowEmptyShould(true)
      .because("@Controller/@RestController must only exist in the 'controller' package");

  @ArchTest
  static final ArchRule SERVICE_ANNOTATION_ONLY_IN_SERVICE_PACKAGE = classes()
      .that()
      .areAnnotatedWith(Service.class)
      .should()
      .resideInAPackage(ROOT + ".service..")
      .allowEmptyShould(true)
      .because("@Service must only exist in the 'service' package");

  @ArchTest
  static final ArchRule ENTITY_ANNOTATION_ONLY_IN_ENTITY_PACKAGE = classes()
      .that()
      .areAnnotatedWith(Entity.class)
      .should()
      .resideInAPackage(ROOT + ".entity..")
      .allowEmptyShould(true)
      .because("@Entity must only exist in the 'entity' package");

  @ArchTest
  static final ArchRule MAPPER_ANNOTATION_ONLY_IN_MAPPER_PACKAGE = classes()
      .that()
      .areAnnotatedWith(Mapper.class)
      .should()
      .resideInAPackage(ROOT + ".mapper..")
      .allowEmptyShould(true)
      .because("@Mapper must only exist in the 'mapper' package");

  @ArchTest
  static final ArchRule REPOSITORIES_ONLY_IN_REPOSITORY_PACKAGE = classes()
      .that()
      .areAssignableTo(JpaRepository.class)
      .should()
      .resideInAPackage(ROOT + ".repository..")
      .allowEmptyShould(true)
      .because("JpaRepository extensions must only exist in the 'repository' package");

  // Layer Dependency Rules

  @ArchTest
  static final ArchRule LAYER_DEPENDENCIES = layeredArchitecture()
      .consideringOnlyDependenciesInAnyPackage(ROOT + "..")
      .layer("Controller")
      .definedBy(ROOT + ".controller..")
      .layer("Service")
      .definedBy(ROOT + ".service..")
      .layer("Repository")
      .definedBy(ROOT + ".repository..")
      .layer("Entity")
      .definedBy(ROOT + ".entity..")
      .layer("Mapper")
      .definedBy(ROOT + ".mapper..")
      .layer("Dto")
      .definedBy(ROOT + ".dto..", ROOT + ".model..")
      .layer("Config")
      .definedBy(ROOT + ".config..")
      .whereLayer("Controller")
      .mayOnlyBeAccessedByLayers("Config")
      .whereLayer("Service")
      .mayOnlyBeAccessedByLayers("Controller", "Config")
      .whereLayer("Repository")
      .mayOnlyBeAccessedByLayers("Service", "Config")
      .whereLayer("Entity")
      .mayOnlyBeAccessedByLayers("Repository", "Service", "Mapper")
      .whereLayer("Mapper")
      .mayOnlyBeAccessedByLayers("Service", "Controller")
      .allowEmptyShould(true);

  // Extra Guards

  @ArchTest
  static final ArchRule CONTROLLERS_SHOULD_NOT_ACCESS_REPOSITORIES_DIRECTLY = noClasses()
      .that()
      .resideInAPackage(ROOT + ".controller..")
      .should()
      .dependOnClassesThat()
      .resideInAPackage(ROOT + ".repository..")
      .allowEmptyShould(true)
      .because("Controllers must not access repositories directly — go through a Service");

  @ArchTest
  static final ArchRule CONTROLLERS_SHOULD_NOT_USE_ENTITIES_DIRECTLY = noClasses()
      .that()
      .resideInAPackage(ROOT + ".controller..")
      .should()
      .dependOnClassesThat()
      .resideInAPackage(ROOT + ".entity..")
      .allowEmptyShould(true)
      .because("Controllers must not use entities directly — use DTOs instead");

  @ArchTest
  static final ArchRule MAPPERS_SHOULD_NOT_CALL_SERVICES = noClasses()
      .that()
      .resideInAPackage(ROOT + ".mapper..")
      .should()
      .dependOnClassesThat()
      .resideInAPackage(ROOT + ".service..")
      .allowEmptyShould(true)
      .because("Mappers must not depend on services");

  @ArchTest
  static final ArchRule CONTROLLERS_SHOULD_NOT_USE_SPRING_DATA_PAGE = noClasses()
      .that()
      .resideInAPackage(ROOT + ".controller..")
      .should()
      .dependOnClassesThat()
      .resideInAnyPackage("org.springframework.data.domain..")
      .allowEmptyShould(true)
      .because(
          "Controllers must not use Spring Data Page/Pageable — return List with pagination headers");
}

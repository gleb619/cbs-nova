package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.starter.config.registrarfixture.AnnotatedHelper;
import cbs.nova.starter.config.registrarfixture.PlainClass;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedGenericBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Pins the four silent early-return branches of {@link SpringHelperBeanDefinitionRegistrar} plus
 * its register / not-overwrite / non-annotated paths. Uses {@link DefaultListableBeanFactory} and
 * {@link AutoConfigurationPackages#register(BeanDefinitionRegistry, String...)} instead of a Spring
 * context, and places the {@code @SpringHelper} fixture in a dedicated test sub-package so the
 * class-path scanner cannot accidentally pick up real helpers from {@code cbs.nova.starter.helper}.
 */
class SpringHelperBeanDefinitionRegistrarTest {

  private static final AnnotationBeanNameGenerator NAME_GENERATOR = AnnotationBeanNameGenerator.INSTANCE;

  private static final AnnotationMetadata IMPORTING_METADATA = AnnotationMetadata
          .introspect(Object.class);

  private final SpringHelperBeanDefinitionRegistrar registrar = new SpringHelperBeanDefinitionRegistrar();

  @Test
  void nonConfigurableBeanFactoryRegistryReturnsSilently() {
    ClasspathOnlyRegistry plainRegistry = new ClasspathOnlyRegistry();

    assertThatCode(
            () -> registrar.registerBeanDefinitions(IMPORTING_METADATA, plainRegistry))
            .doesNotThrowAnyException();

    assertThat(plainRegistry.getRegisteredBeanNames())
            .as("non-CBF registry must not record any registrations")
            .isEmpty();
  }

  @Test
  void missingAutoConfigurationPackagesReturnsSilently() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    // Deliberately do NOT call AutoConfigurationPackages.register(...) — get() throws ISE which
    // the registrar must swallow + debug-log + return.

    assertThatCode(
            () -> registrar.registerBeanDefinitions(IMPORTING_METADATA, beanFactory))
            .doesNotThrowAnyException();

    assertThat(beanFactory.getBeanDefinitionNames())
            .as("missing AutoConfigurationPackages must yield zero registrations")
            .isEmpty();
  }

  @Test
  void emptyPackagesListReturnsSilently() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    AutoConfigurationPackages.register(beanFactory);

    int before = beanFactory.getBeanDefinitionCount();
    assertThatCode(
            () -> registrar.registerBeanDefinitions(IMPORTING_METADATA, beanFactory))
            .doesNotThrowAnyException();
    int after = beanFactory.getBeanDefinitionCount();

    assertThat(after)
            .as("empty auto-config packages must yield zero new registrations")
            .isEqualTo(before);
  }

  @Test
  void annotatedHelperIsRegisteredWithSingletonScope() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    AutoConfigurationPackages.register(beanFactory, "cbs.nova.starter.config.registrarfixture");

    registrar.registerBeanDefinitions(IMPORTING_METADATA, beanFactory);

    String expectedName = NAME_GENERATOR.generateBeanName(
            beanDefOf(AnnotatedHelper.class), beanFactory);

    assertThat(beanFactory.containsBeanDefinition(expectedName))
            .as("annotated fixture must be present under AnnotationBeanNameGenerator name")
            .isTrue();

    BeanDefinition registered = beanFactory.getBeanDefinition(expectedName);
    assertThat(registered.getBeanClassName())
            .isEqualTo(AnnotatedHelper.class.getName());
    assertThat(registered.getScope())
            .as("registered helpers must be singletons")
            .isEqualTo(BeanDefinition.SCOPE_SINGLETON);
    assertThat(beanFactory.getBeanDefinitionNames())
            .as("plain class in same package must NOT be registered")
            .doesNotContain(beanNameFor(PlainClass.class));
  }

  @Test
  void preexistingBeanNameBranchIsSkippedSilently() {
    // Inner-loop early-exit: when the candidate's generated name is already in the registry,
    // the registrar must `continue` without touching the registry, throwing, or registering.
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    AutoConfigurationPackages.register(beanFactory, "cbs.nova.starter.config.registrarfixture");

    String expectedName = NAME_GENERATOR.generateBeanName(
            beanDefOf(AnnotatedHelper.class), beanFactory);
    BeanDefinition sentinel = new GenericBeanDefinition();
    sentinel.setBeanClassName("cbs.nova.starter.config.registrarfixture.Sentinel");
    sentinel.setScope(BeanDefinition.SCOPE_PROTOTYPE);
    beanFactory.registerBeanDefinition(expectedName, sentinel);

    int before = beanFactory.getBeanDefinitionCount();
    assertThatCode(
            () -> registrar.registerBeanDefinitions(IMPORTING_METADATA, beanFactory))
            .doesNotThrowAnyException();

    assertThat(beanFactory.getBeanDefinitionCount())
            .as("pre-existing bean name must cause the inner loop to `continue` without registration")
            .isEqualTo(before);
    assertThat(beanFactory.getBeanDefinition(expectedName).getBeanClassName())
            .as("pre-existing bean name's definition must remain the sentinel")
            .isEqualTo("cbs.nova.starter.config.registrarfixture.Sentinel");
  }

  @Test
  void preexistingBeanDefinitionIsNotOverwritten() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    AutoConfigurationPackages.register(beanFactory, "cbs.nova.starter.config.registrarfixture");

    String expectedName = NAME_GENERATOR.generateBeanName(
            beanDefOf(AnnotatedHelper.class), beanFactory);

    BeanDefinition sentinel = new GenericBeanDefinition();
    sentinel.setBeanClassName("cbs.nova.starter.config.registrarfixture.Sentinel");
    sentinel.setScope(BeanDefinition.SCOPE_PROTOTYPE);
    beanFactory.registerBeanDefinition(expectedName, sentinel);

    registrar.registerBeanDefinitions(IMPORTING_METADATA, beanFactory);

    BeanDefinition after = beanFactory.getBeanDefinition(expectedName);
    assertThat(after.getBeanClassName())
            .as("existing definition must NOT be overwritten")
            .isEqualTo("cbs.nova.starter.config.registrarfixture.Sentinel");
    assertThat(after.getScope())
            .as("existing definition's scope must be preserved")
            .isEqualTo(BeanDefinition.SCOPE_PROTOTYPE);
  }

  @Test
  void nonAnnotatedClassInScannedPackageIsNotRegistered() {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    AutoConfigurationPackages.register(beanFactory, "cbs.nova.starter.config.registrarfixture");

    registrar.registerBeanDefinitions(IMPORTING_METADATA, beanFactory);

    assertThat(beanFactory.getBeanDefinitionNames())
            .as("plain class must not be registered")
            .doesNotContain(beanNameFor(PlainClass.class));

    assertThat(beanFactory.containsBeanDefinition(beanNameFor(AnnotatedHelper.class)))
            .as("annotated sibling must still be registered")
            .isTrue();
  }

  private static String beanNameFor(Class<?> type) {
    return NAME_GENERATOR.generateBeanName(
            new AnnotatedGenericBeanDefinition(type), new DefaultListableBeanFactory());
  }

  private static BeanDefinition beanDefOf(Class<?> type) {
    return new AnnotatedGenericBeanDefinition(type);
  }

  /**
   * Minimal {@link BeanDefinitionRegistry} that is NOT a {@link ConfigurableBeanFactory} — used to
   * exercise the first early-return branch.
   */
  private static final class ClasspathOnlyRegistry implements BeanDefinitionRegistry {

    private final List<String> registered = new java.util.ArrayList<>();

    @Override
    public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition) {
      registered.add(beanName);
    }

    @Override
    public void removeBeanDefinition(String beanName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BeanDefinition getBeanDefinition(String beanName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsBeanDefinition(String beanName) {
      return false;
    }

    @Override
    public String[] getBeanDefinitionNames() {
      return new String[0];
    }

    @Override
    public int getBeanDefinitionCount() {
      return 0;
    }

    @Override
    public boolean isBeanNameInUse(String beanName) {
      return false;
    }

    @Override
    public void registerAlias(String name, String alias) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeAlias(String alias) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAlias(String name) {
      return false;
    }

    @Override
    public String[] getAliases(String name) {
      return new String[0];
    }

    List<String> getRegisteredBeanNames() {
      return List.copyOf(registered);
    }
  }
}

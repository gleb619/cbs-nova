package cbs.nova.dsl.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GeneratedClassProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GeneratedClassRegistryTest {

  private GeneratedClassRegistry registry;

  @BeforeEach
  void setUp() {
    registry = new GeneratedClassRegistry();
  }

  private static GeneratedClassDescriptor descriptor(String name, DslType type) {
    return new GeneratedClassDescriptor(
            name,
            type,
            "v1",
            "default",
            Runnable.class,
            Runnable.class,
            String.class,
            String.class,
            "{}");
  }

  private static GeneratedClassDescriptor process(String name) {
    return descriptor(name, DslType.PROCESS);
  }

  private static GeneratedClassDescriptor transaction(String name) {
    return descriptor(name, DslType.TRANSACTION);
  }

  private static GeneratedClassDescriptor function(String name) {
    return descriptor(name, DslType.FUNCTION);
  }

  @Test
  void registerProcessIsFoundByFindProcess() {
    registry.register(process("OrderProcess"));

    assertThat(registry.findProcess("OrderProcess")).isPresent();
    assertThat(registry.findTransaction("OrderProcess")).isEmpty();
  }

  @Test
  void registerTransactionIsFoundByFindTransaction() {
    registry.register(transaction("PayTx"));

    assertThat(registry.findTransaction("PayTx")).isPresent();
    assertThat(registry.findProcess("PayTx")).isEmpty();
  }

  @Test
  void registerFunctionLandsInNeitherMap() {
    registry.register(function("Helper"));

    assertThat(registry.findProcess("Helper")).isEmpty();
    assertThat(registry.findTransaction("Helper")).isEmpty();
    assertThat(registry.processes()).isEmpty();
    assertThat(registry.transactions()).isEmpty();
  }

  @Test
  void secondRegisterSameNameOverridesFirstForProcess() {
    registry.register(process("OrderProcess", "v1"));
    registry.register(process("OrderProcess", "v2"));

    assertThat(registry.processes()).hasSize(1);
    assertThat(registry.findProcess("OrderProcess"))
            .get()
            .extracting(GeneratedClassDescriptor::version)
            .isEqualTo("v2");
  }

  @Test
  void secondRegisterSameNameOverridesFirstForTransaction() {
    registry.register(transaction("PayTx", "v1"));
    registry.register(transaction("PayTx", "v2"));

    assertThat(registry.transactions()).hasSize(1);
    assertThat(registry.findTransaction("PayTx"))
            .get()
            .extracting(GeneratedClassDescriptor::version)
            .isEqualTo("v2");
  }

  @Test
  void findProcessReturnsEmptyForUnknownName() {
    assertThat(registry.findProcess("nope")).isEmpty();
  }

  @Test
  void findTransactionReturnsEmptyForUnknownName() {
    assertThat(registry.findTransaction("nope")).isEmpty();
  }

  @Test
  void processesListIsImmutable() {
    registry.register(process("P1"));
    var snapshot = registry.processes();

    assertThatThrownBy(() -> snapshot.add(process("P2")))
            .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void transactionsListIsImmutable() {
    registry.register(transaction("T1"));
    var snapshot = registry.transactions();

    assertThatThrownBy(() -> snapshot.add(transaction("T2")))
            .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void processesListIsASnapshot() {
    registry.register(process("P1"));
    var snapshot = registry.processes();

    registry.register(process("P2"));
    registry.register(process("P3"));

    assertThat(snapshot).hasSize(1);
    assertThat(registry.processes()).hasSize(3);
  }

  @Test
  void transactionsListIsASnapshot() {
    registry.register(transaction("T1"));
    var snapshot = registry.transactions();

    registry.register(transaction("T2"));
    registry.register(transaction("T3"));

    assertThat(snapshot).hasSize(1);
    assertThat(registry.transactions()).hasSize(3);
  }

  @Test
  void registerProviderDelegatesViaDescriptor() {
    var expected = process("FromProvider");
    GeneratedClassProvider provider = () -> expected;

    registry.register(provider);

    assertThat(registry.findProcess("FromProvider")).contains(expected);
    assertThat(registry.processes()).containsExactly(expected);
  }

  @Test
  void findProviderReturnsRegisteredProvider() {
    GeneratedClassProvider provider = () -> process("FromProvider");

    registry.register(provider);

    assertThat(registry.findProvider("FromProvider")).contains(provider);
    assertThat(registry.findProvider("nope")).isEmpty();
  }

  @Test
  void findFilenameComesFromProvider() {
    GeneratedClassProvider provider = new GeneratedClassProvider() {
      @Override
      public GeneratedClassDescriptor descriptor() {
        return process("FromProvider");
      }

      @Override
      public String filename() {
        return "FromProvider.java";
      }
    };

    registry.register(provider);

    assertThat(registry.findFilename("FromProvider")).contains("FromProvider.java");
    assertThat(registry.findFilename("nope")).isEmpty();
  }

  @Test
  void registerDescriptorOnlyDoesNotExposeProvider() {
    registry.register(process("DescOnly"));

    assertThat(registry.findProvider("DescOnly")).isEmpty();
    assertThat(registry.findFilename("DescOnly")).isEmpty();
  }

  @Test
  void noArgConstructorDoesNotThrow() {
    assertThatCode(GeneratedClassRegistry::new).doesNotThrowAnyException();
  }

  private static GeneratedClassDescriptor process(String name, String version) {
    return new GeneratedClassDescriptor(
            name,
            DslType.PROCESS,
            version,
            "default",
            Runnable.class,
            Runnable.class,
            String.class,
            String.class,
            "{}");
  }

  private static GeneratedClassDescriptor transaction(String name, String version) {
    return new GeneratedClassDescriptor(
            name,
            DslType.TRANSACTION,
            version,
            "default",
            Runnable.class,
            Runnable.class,
            String.class,
            String.class,
            "{}");
  }
}

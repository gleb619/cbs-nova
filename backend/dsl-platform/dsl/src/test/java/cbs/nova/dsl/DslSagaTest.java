package cbs.nova.dsl;

import cbs.nova.dsl.exception.DslSagaCompensationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DslSagaTest {

  @Test
  void compensationsRunInReverseOrder() {
    var order = new ArrayList<String>();
    var saga = DslSaga.create();
    saga.addCompensation(() -> order.add("A"));
    saga.addCompensation(() -> order.add("B"));
    saga.addCompensation(() -> order.add("C"));

    saga.compensate();

    assertThat(order).containsExactly("C", "B", "A");
    assertThat(saga.hasCompensations()).isFalse();
  }

  @Test
  void emptySagaCompensationIsNoOp() {
    var saga = DslSaga.create();

    saga.compensate();

    assertThat(saga.hasCompensations()).isFalse();
  }

  @Test
  void hasCompensationsReflectsPendingCompensations() {
    var saga = DslSaga.create();
    assertThat(saga.hasCompensations()).isFalse();

    saga.addCompensation(() -> {
    });
    assertThat(saga.hasCompensations()).isTrue();

    saga.compensate();
    assertThat(saga.hasCompensations()).isFalse();
  }

  @Test
  void compensationsAreClearedBeforeTheyRun() {
    var saga = DslSaga.create();
    var cleared = new ArrayList<Boolean>();
    saga.addCompensation(() -> cleared.add(saga.hasCompensations()));

    saga.compensate();

    assertThat(cleared).containsExactly(false);
  }

  @Test
  void allCompensationsRunEvenIfOneThrows() {
    var order = new ArrayList<String>();
    var saga = DslSaga.create();
    RuntimeException bError = new RuntimeException("B");
    saga.addCompensation(() -> order.add("A"));
    saga.addCompensation(() -> {
      throw bError;
    });
    saga.addCompensation(() -> order.add("C"));

    assertThatThrownBy(saga::compensate)
            .isInstanceOf(DslSagaCompensationException.class)
            .hasCause(bError)
            .satisfies(ex -> assertThat(ex.getSuppressed()).isEmpty());

    assertThat(order).containsExactly("C", "A");
  }

  @Test
  void multipleCompensationFailuresAreAggregated() {
    var order = new ArrayList<String>();
    var saga = DslSaga.create();
    RuntimeException bError = new RuntimeException("B");
    RuntimeException aError = new RuntimeException("A");
    saga.addCompensation(() -> {
      throw aError;
    });
    saga.addCompensation(() -> {
      throw bError;
    });
    saga.addCompensation(() -> order.add("C"));

    assertThatThrownBy(saga::compensate)
            .isInstanceOf(DslSagaCompensationException.class)
            .hasCause(bError)
            .satisfies(ex -> assertThat(ex.getSuppressed()).containsExactly(aError));

    assertThat(order).containsExactly("C");
  }

  @Test
  void parallelCompensationExecutesAllCompensations() {
    var order = new ArrayList<String>();
    var saga = DslSaga.create(DslSaga.Options.builder().setParallelCompensation(true).build());
    RuntimeException bError = new RuntimeException("B");
    saga.addCompensation(() -> order.add("A"));
    saga.addCompensation(() -> {
      throw bError;
    });
    saga.addCompensation(() -> order.add("C"));

    assertThatThrownBy(saga::compensate)
            .isInstanceOf(DslSagaCompensationException.class)
            .hasCause(bError);

    assertThat(order).containsExactly("C", "A");
  }
}

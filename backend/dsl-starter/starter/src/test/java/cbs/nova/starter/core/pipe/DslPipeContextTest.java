package cbs.nova.starter.core.pipe;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.config.ContextFactory;
import org.junit.jupiter.api.Test;

class DslPipeContextTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void of_createsContextWithFreshAttributesMap() {
    DslPipeContext first = DslPipeContext.of("ping", contextFactory.of("in", ExecutionMode.RUN),
            ExecutionMode.RUN, "run-1");
    DslPipeContext second = DslPipeContext.of("ping", contextFactory.of("in", ExecutionMode.RUN),
            ExecutionMode.RUN, "run-1");

    first.setAttribute("key", "value");

    assertThat(first.getAttribute("key")).isEqualTo("value");
    assertThat(second.getAttribute("key")).isNull();
  }

  @Test
  void builder_omittingAttributes_stillProvidesUsableMap() {
    DslPipeContext built = DslPipeContext.builder()
            .name("ping")
            .dslContext(contextFactory.of("in", ExecutionMode.RUN))
            .mode(ExecutionMode.RUN)
            .runId("run-1")
            .build();

    built.setAttribute("key", "value");

    assertThat(built.getAttribute("key")).isEqualTo("value");
  }

  @Test
  void withDslContext_sharesAttributesMap() {
    DslPipeContext original = DslPipeContext.of("ping", contextFactory.of("in", ExecutionMode.RUN),
            ExecutionMode.RUN, "run-1");
    original.setAttribute("key", "original");

    DslPipeContext copy = original.withDslContext(contextFactory.of("in", ExecutionMode.PREVIEW));

    assertThat(copy).isNotSameAs(original);
    assertThat(copy.dslContext()).isNotSameAs(original.dslContext());
    assertThat(copy.name()).isEqualTo(original.name());
    assertThat(copy.mode()).isEqualTo(original.mode());
    assertThat(copy.runId()).isEqualTo(original.runId());

    // Mutating an attribute on the copy is visible on the original.
    copy.setAttribute("key", "from-copy");
    assertThat(original.getAttribute("key")).isEqualTo("from-copy");

    // Mutating an attribute on the original is visible on the copy.
    original.setAttribute("another", "from-original");
    assertThat(copy.getAttribute("another")).isEqualTo("from-original");
  }

  @Test
  void setAttribute_nullRemovesKey() {
    DslPipeContext context = DslPipeContext.of("ping", contextFactory.of("in", ExecutionMode.RUN),
            ExecutionMode.RUN, "run-1");

    context.setAttribute("key", "value");
    assertThat(context.getAttribute("key")).isEqualTo("value");

    context.setAttribute("key", null);
    assertThat(context.getAttribute("key")).isNull();
  }

  @Test
  void getAttribute_typed_returnsNullWhenTypeDoesNotMatch() {
    DslPipeContext context = DslPipeContext.of("ping", contextFactory.of("in", ExecutionMode.RUN),
            ExecutionMode.RUN, "run-1");
    context.setAttribute("key", "a-string");

    assertThat(context.getAttribute("key", String.class)).isEqualTo("a-string");
    assertThat(context.getAttribute("key", Integer.class)).isNull();
  }
}

package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.model.SimpleContext;
import com.github.benmanes.caffeine.cache.Caffeine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.fake.FakeConfig;
import cbs.nova.dsl.fake.FakeEntry;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import org.junit.jupiter.api.Test;

import java.util.Optional;

class FakeHelperInterceptorTest {

  @Test
  void shortCircuitsConfiguredHelperWithResponse() {
    var registry = new RunScopedFakeConfig(Caffeine.newBuilder().build());
    var response = "fake-response";
    registry.register("run-1",
            FakeConfig.of(new FakeEntry("helper", "httpCall", response)));
    var recorder = mock(ExternalCallRecorder.class);
    var interceptor = new FakeHelperInterceptor(registry, recorder);

    Context<?> ctx = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-1")
            .build();
    Optional<Result<?>> result = interceptor.intercept("httpCall", ctx);

    assertThat(result).isPresent();
    assertThat(result.get().isSuccess()).isTrue();
    assertThat(result.get().value()).isEqualTo(response);
    verify(recorder).record(eq("helper"), eq("httpCall"), eq("execute"), eq(response));
  }

  @Test
  void passesThroughWhenNoConfigRegistered() {
    var registry = new RunScopedFakeConfig(Caffeine.newBuilder().build());
    var recorder = mock(ExternalCallRecorder.class);
    var interceptor = new FakeHelperInterceptor(registry, recorder);

    Context<?> ctx = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-missing")
            .build();
    Optional<Result<?>> result = interceptor.intercept("httpCall", ctx);

    assertThat(result).isEmpty();
    verifyNoInteractions(recorder);
  }

  @Test
  void passesThroughWhenHelperNotFaked() {
    var registry = new RunScopedFakeConfig(Caffeine.newBuilder().build());
    registry.register("run-2",
            FakeConfig.of(new FakeEntry("helper", "httpCall", "fake")));
    var recorder = mock(ExternalCallRecorder.class);
    var interceptor = new FakeHelperInterceptor(registry, recorder);

    Context<?> ctx = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-2")
            .build();
    Optional<Result<?>> result = interceptor.intercept("otherHelper", ctx);

    assertThat(result).isEmpty();
    verifyNoInteractions(recorder);
  }

  @Test
  void fallsBackToFunctionType() {
    var registry = new RunScopedFakeConfig(Caffeine.newBuilder().build());
    var response = "fn-result";
    registry.register("run-3",
            FakeConfig.of(new FakeEntry("function", "myFn", response)));
    var recorder = mock(ExternalCallRecorder.class);
    var interceptor = new FakeHelperInterceptor(registry, recorder);

    Context<?> ctx = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-3")
            .build();
    Optional<Result<?>> result = interceptor.intercept("myFn", ctx);

    assertThat(result).isPresent();
    assertThat(result.get().value()).isEqualTo(response);
    verify(recorder).record(eq("helper"), eq("myFn"), eq("execute"), eq(response));
  }
}

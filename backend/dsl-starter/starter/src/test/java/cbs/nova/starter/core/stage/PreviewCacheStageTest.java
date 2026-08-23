package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewMetricsSnapshot;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.cache.PreviewCacheKey;
import cbs.nova.starter.cache.PreviewResultCache;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PreviewCacheStageTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void cacheHitShortCircuitsAndReturnsCachedReportWithoutCallingNext() {
    PreviewResultCache cache = mock(PreviewResultCache.class);
    PreviewReport cached = previewReport("hit-output");
    PreviewCacheKey key = new PreviewCacheKey("Ping", "", "input-hash");
    when(cache.get(any())).thenReturn(cached);

    DslPipeContext pipeContext = newPipeContext();
    AtomicBoolean nextCalled = new AtomicBoolean(false);
    DslPipeStage.Next next = c -> {
      nextCalled.set(true);
      return Result.success("fresh");
    };

    Result<?> result = new PreviewCacheStage(cache).execute(pipeContext, next);

    assertThat(nextCalled.get()).isFalse();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isSameAs(cached);

    ArgumentCaptor<PreviewCacheKey> keyCaptor = ArgumentCaptor.forClass(PreviewCacheKey.class);
    verify(cache, times(1)).get(keyCaptor.capture());
    assertThat(keyCaptor.getValue().processName()).isEqualTo("Ping");

    assertThat(pipeContext.getAttribute("previewCacheKey", PreviewCacheKey.class))
        .isNotNull();
    verify(cache, never()).put(any(), any());
  }

  @Test
  void cacheMissDelegatesToNextAndStoresSuccessfulPreviewReport() {
    PreviewResultCache cache = mock(PreviewResultCache.class);
    when(cache.get(any())).thenReturn(null);

    DslPipeContext pipeContext = newPipeContext();
    PreviewReport downstream = previewReport("downstream-output");
    DslPipeStage.Next next = c -> Result.success(downstream);

    Result<?> result = new PreviewCacheStage(cache).execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isSameAs(downstream);

    ArgumentCaptor<PreviewCacheKey> keyCaptor = ArgumentCaptor.forClass(PreviewCacheKey.class);
    ArgumentCaptor<PreviewReport> valueCaptor = ArgumentCaptor.forClass(PreviewReport.class);
    verify(cache, times(1)).put(keyCaptor.capture(), valueCaptor.capture());
    assertThat(keyCaptor.getValue().processName()).isEqualTo("Ping");
    assertThat(valueCaptor.getValue()).isSameAs(downstream);
  }

  @Test
  void nullCacheBypassesCachingEntirely() {
    DslPipeContext pipeContext = newPipeContext();
    AtomicBoolean nextCalled = new AtomicBoolean(false);
    DslPipeStage.Next next = c -> {
      nextCalled.set(true);
      return Result.success("plain");
    };

    Result<?> result = new PreviewCacheStage(null).execute(pipeContext, next);

    assertThat(nextCalled.get()).isTrue();
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("plain");
    assertThat(pipeContext.getAttribute("previewCacheKey", PreviewCacheKey.class))
        .isNull();
  }

  @Test
  void nonPreviewReportSuccessIsNotCached() {
    PreviewResultCache cache = mock(PreviewResultCache.class);
    when(cache.get(any())).thenReturn(null);

    DslPipeContext pipeContext = newPipeContext();
    DslPipeStage.Next next = c -> Result.success("raw-value");

    Result<?> result = new PreviewCacheStage(cache).execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("raw-value");
    verify(cache, never()).put(any(), any());
  }

  @Test
  void failedResultIsNotCached() {
    PreviewResultCache cache = mock(PreviewResultCache.class);
    when(cache.get(any())).thenReturn(null);

    DslPipeContext pipeContext = newPipeContext();
    RuntimeException boom = new RuntimeException("boom");
    DslPipeStage.Next next = c -> Result.failure(boom);

    Result<?> result = new PreviewCacheStage(cache).execute(pipeContext, next);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isSameAs(boom);
    verify(cache, never()).put(any(), any());
  }

  @Test
  void exceptionFromNextPropagatesAndCachePutIsNotInvoked() {
    PreviewResultCache cache = mock(PreviewResultCache.class);
    when(cache.get(any())).thenReturn(null);

    DslPipeContext pipeContext = newPipeContext();
    DslPipeStage.Next next = c -> {
      throw new IllegalStateException("downstream boom");
    };

    assertThatThrownBy(() -> new PreviewCacheStage(cache).execute(pipeContext, next))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("downstream boom");

    verify(cache, never()).put(any(), any());
  }

  private DslPipeContext newPipeContext() {
    Context<Object> ctx = contextFactory.of("body", ExecutionMode.PREVIEW, "run-1");
    return new DslPipeContext("Ping", ctx, ExecutionMode.PREVIEW, "run-1");
  }

  private static PreviewReport previewReport(Object output) {
    return new PreviewReport(
        "Ping",
        ExecutionMode.PREVIEW,
        true,
        output,
        List.of(),
        List.of(),
        Map.of(),
        null,
        List.of(),
        null,
        null);
  }
}

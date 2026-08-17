package cbs.nova.starter.core.stage;

import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.starter.cache.PreviewCacheKey;
import cbs.nova.starter.cache.PreviewResultCache;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

@Slf4j
@RequiredArgsConstructor
public final class PreviewCacheStage implements DslPipeStage {

  private final PreviewResultCache cache;
  private final PreviewCacheKeyBuilder keyBuilder = new PreviewCacheKeyBuilder();

  @Override
  public @NonNull Result<?> execute(@NonNull DslPipeContext context, @NonNull Next next) {
    if (cache == null) {
      return next.proceed(context);
    }
    PreviewCacheKey key = keyBuilder.build(context.getName(), context.getDslContext());
    context.setAttribute("previewCacheKey", key);
    PreviewReport cached = cache.get(key);
    if (cached != null) {
      log.debug("Preview cache hit for {}", context.getName());
      return Result.success(cached);
    }
    log.debug("Preview cache miss for {}", context.getName());
    Result<?> result = next.proceed(context);
    if (result.isSuccess() && result.value() instanceof PreviewReport report) {
      cache.put(key, report);
    }
    return result;
  }
}

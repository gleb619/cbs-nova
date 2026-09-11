package cbs.nova.dsl.builder.config;

import cbs.nova.dsl.builder.service.FileBuffer;
import cbs.nova.dsl.builder.service.FileBulkhead;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BuilderServiceConfiguration {

  @Bean
  public FileBuffer fileBuffer(DslBuilderProperties properties) {
    return new FileBuffer(pendingCache(properties, Ticker.systemTicker()));
  }

  @Bean
  public FileBulkhead fileBulkhead(DslBuilderProperties properties) {
    var files = properties.files();
    return new FileBulkhead(
            new Semaphore(files.readBulkheadPermits()),
            new Semaphore(files.writeBulkheadPermits()),
            files.acquireTimeoutSeconds());
  }

  public static Cache<String, String> pendingCache(DslBuilderProperties properties, Ticker ticker) {
    int maxEntries = Math.max(1, properties.fileBuffer().maxEntries());
    long ttlSeconds = Math.max(1L, properties.fileBuffer().expireAfterWriteSeconds());
    return Caffeine.newBuilder()
            .maximumSize(maxEntries)
            .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
            .ticker(ticker)
            .build();
  }
}

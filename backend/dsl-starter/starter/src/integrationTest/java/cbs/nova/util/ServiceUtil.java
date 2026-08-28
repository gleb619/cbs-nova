package cbs.nova.util;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.service.TemporalDslProcessService;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import tools.jackson.databind.ObjectMapper;

public class ServiceUtil {

  public static TemporalDslProcessService newService(ContextFactory contextFactory) {
    return createService(
            contextFactory, new InMemoryDslRunRepository(), new ObjectMapper());
  }

  public static TemporalDslProcessService createService(
          ContextFactory contextFactory,
          DslRunRepository runRepository,
          ObjectMapper objectMapper) {
    return new TemporalDslProcessService(
            contextFactory,
            runRepository,
            objectMapper,
            sameThreadExecutor(),
            disabledScheduledExecutor(),
            Duration.ofSeconds(30),
            Duration.ofMinutes(5),
            false);
  }

  private static @NonNull ThreadPoolTaskExecutor sameThreadExecutor() {
    ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor() {
      @Override
      public void execute(@NonNull Runnable command) {
        command.run();
      }
    };
    exec.setCorePoolSize(1);
    exec.setMaxPoolSize(1);
    exec.setQueueCapacity(0);
    exec.setThreadNamePrefix("cbs-nova-dsl-sync-");
    exec.initialize();
    return exec;
  }

  private static @NonNull ScheduledExecutorService disabledScheduledExecutor() {
    ThreadFactory tf = r -> {
      Thread t = new Thread(r, "cbs-nova-dsl-healthcheck-disabled");
      t.setDaemon(true);
      return t;
    };
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(tf);
    exec.shutdownNow();
    return exec;
  }

}

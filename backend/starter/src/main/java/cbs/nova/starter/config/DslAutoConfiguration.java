package cbs.nova.starter.config;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.DslRunRepository;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.TemporalProcessLauncher;
import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.ExternalCallTracker;
import cbs.nova.starter.listeners.ExternalCallListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@AutoConfiguration
public class DslAutoConfiguration {

  @Value("${dsl.source-dir:}")
  private String sourceDirProperty;

  @Autowired(required = false)
  private List<ExternalCallListener> externalCallListeners;

  @Autowired(required = false)
  private ExternalCallTracker externalCallTracker;

  @Autowired(required = false)
  private TemporalProcessLauncher temporalProcessLauncher;

  @Autowired(required = false)
  private TransactionInvoker transactionInvoker;

  @PostConstruct
  public void loadDslDefinitions() {
    if (sourceDirProperty != null && !sourceDirProperty.isBlank()) {
      var dir = Path.of(sourceDirProperty);
      if (!Files.isDirectory(dir)) {
        throw new IllegalStateException(
                "dsl.source-dir does not exist or is not a directory: " + dir);
      }

      new DefinitionLoader().load(dir, GlobalManager.getInstance());
    }
    registerHelperResolvers();
    registerExternalCallListeners();
    registerTemporalProcessLauncher();
    registerTransactionInvoker();
  }

  @Bean(name = "jacksonObjectMapper")
  @ConditionalOnMissingBean(ObjectMapper.class)
  public ObjectMapper jacksonObjectMapper() {
    return Jackson2ObjectMapperBuilder.json().build();
  }

  @Bean
  @ConditionalOnMissingBean(DslRunRepository.class)
  public DslRunRepository dslRunRepository() {
    return new InMemoryDslRunRepository();
  }

  private void registerHelperResolvers() {
    GlobalManager.getInstance().registerHelperResolvers();
  }

  private void registerExternalCallListeners() {
    if (externalCallListeners == null || externalCallListeners.isEmpty()
            || externalCallTracker == null) {
      return;
    }

    for (ExternalCallListener listener : externalCallListeners) {
      externalCallTracker.registerListener(listener);
    }
  }

  private void registerTemporalProcessLauncher() {
    if (temporalProcessLauncher != null) {
      DslConfig.dslConfig().temporalProcessLauncher().replace(temporalProcessLauncher);
    }
  }

  private void registerTransactionInvoker() {
    if (transactionInvoker != null) {
      DslConfig.dslConfig().transactionInvoker().replace(transactionInvoker);
    }
  }
}

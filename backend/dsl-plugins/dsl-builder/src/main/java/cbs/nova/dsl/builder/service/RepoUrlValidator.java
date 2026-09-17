package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.utils.UrlSafetyValidator;

/** Thin adapter over the shared {@link UrlSafetyValidator} (T517) for repo clone URLs. */
final class RepoUrlValidator {

  private static final String LABEL = "repoUrl";

  private RepoUrlValidator() {
  }

  static void validate(String repoUrl, DslBuilderProperties properties) {
    var config = new UrlSafetyValidator.Config(
            properties.allowedRepoSchemes(),
            properties.allowedRepoHosts(),
            properties.allowPlainHttpRepo(),
            true,
            false);
    UrlSafetyValidator.validate(repoUrl, config, LABEL);
  }
}

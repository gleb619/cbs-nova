package cbs.nova.dsl.builder.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cbs.dsl.builder")
public record DslBuilderProperties(
        Path workspaceDir,
        Duration cleanupInterval,
        Duration sessionTtl,
        Path gradleJavaHome,
        String dslVersion,
        String temporalVersion,
        String defaultBuildVersion,
        List<String> buildTasks,
        List<String> sourceFolders,
        String templatesDir) {
}
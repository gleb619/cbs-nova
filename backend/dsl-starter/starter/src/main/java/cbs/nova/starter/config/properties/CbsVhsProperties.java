package cbs.nova.starter.config.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the VHS execution recorder ({@code cbs.vhs.*}).
 *
 * <p>
 * Recording is opt-in and off by default. When {@code enabled=false} no sink or recorder beans are
 * registered and the hot execution path is unchanged.
 */
@Validated
@ConfigurationProperties(prefix = "cbs.vhs")
public record CbsVhsProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue List<String> recordableRoutes,
        @DefaultValue("local") SinkType sinkType,
        @Valid @DefaultValue LocalSink local) {

  public CbsVhsProperties {
    recordableRoutes = recordableRoutes == null ? List.of() : List.copyOf(recordableRoutes);
    sinkType = sinkType == null ? SinkType.local : sinkType;
    local = local == null ? new LocalSink(null, 0, 0L) : local;
  }

  /**
   * Local file-system tape sink settings.
   */
  public record LocalSink(
          Path path,
          @DefaultValue("0") @Min(0) int maxTapes,
          @DefaultValue("0") @Min(0) long maxBytes) {

    public LocalSink {
      if (path == null) {
        path = Path.of(System.getProperty("java.io.tmpdir"), "cbs-nova", "vhs");
      }
      if (maxTapes < 0) {
        maxTapes = 0;
      }
      if (maxBytes < 0) {
        maxBytes = 0L;
      }
    }
  }

  /**
   * Supported sink backends. Only {@code local} is implemented; {@code object_storage} is reserved
   * for a follow-up task.
   */
  public enum SinkType {
    local, object_storage
  }
}

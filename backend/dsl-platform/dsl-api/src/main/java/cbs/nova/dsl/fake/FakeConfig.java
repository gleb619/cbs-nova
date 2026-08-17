package cbs.nova.dsl.fake;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record FakeConfig(@NonNull List<FakeEntry> entries) {

  public FakeConfig {
    entries = entries == null ? List.of() : List.copyOf(entries);
  }

  public static @NonNull FakeConfig empty() {
    return new FakeConfig(List.of());
  }

  public static @NonNull FakeConfig of(@NonNull FakeEntry... entries) {
    return new FakeConfig(List.of(entries));
  }

  public @NonNull Map<String, Object> toCodeMap(@NonNull String type) {
    return entries.stream()
            .filter(e -> e.type().equals(type))
            .filter(e -> e.response() != null)
            .collect(Collectors.toUnmodifiableMap(
                    FakeEntry::code,
                    FakeEntry::response,
                    (a, b) -> b));
  }

  public @Nullable Object findResponse(@NonNull String type, @NonNull String code) {
    return toCodeMap(type).get(code);
  }
}

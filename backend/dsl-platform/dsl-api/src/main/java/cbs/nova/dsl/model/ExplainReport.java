package cbs.nova.dsl.model;

import org.jspecify.annotations.NonNull;

public record ExplainReport(
        @NonNull String name,
        @NonNull String description,
        @NonNull String mermaid) {

  public @NonNull ExplainReport merge(@NonNull ExplainReport other) {
    return new ExplainReport(
            this.name,
            joinMarkdown(this.description, other.description),
            joinMermaid(this.mermaid, other.mermaid));
  }

  public @NonNull ExplainReport truncateTo(int budgetChars) {
    if (budgetChars < 0) {
      return new ExplainReport(name, "", "");
    }
    int totalLength = description.length() + mermaid.length();
    if (totalLength <= budgetChars) {
      return this;
    }
    int descriptionLimit = Math.min(description.length(), budgetChars);
    String truncatedDescription = description.substring(0, descriptionLimit);
    int remaining = budgetChars - descriptionLimit;
    String truncatedMermaid = remaining <= 0
            ? ""
            : mermaid.substring(0, Math.min(mermaid.length(), remaining));
    return new ExplainReport(name, truncatedDescription, truncatedMermaid);
  }

  private static @NonNull String joinMarkdown(@NonNull String first, @NonNull String second) {
    if (first.isEmpty()) {
      return second;
    }
    if (second.isEmpty() || "<!-- NONE -->".equals(second)) {
      return first;
    }
    if ("<!-- NONE -->".equals(first)) {
      return second;
    }
    return first + "\n\n" + second;
  }

  private static @NonNull String joinMermaid(@NonNull String first, @NonNull String second) {
    if (first.isEmpty()) {
      return second;
    }
    if (second.isEmpty()) {
      return first;
    }
    return first + "\n" + second;
  }
}

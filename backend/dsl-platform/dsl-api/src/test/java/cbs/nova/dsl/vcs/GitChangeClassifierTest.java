package cbs.nova.dsl.vcs;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GitChangeClassifierTest {

  @Test
  void nullInputsAreTreatedAsEmpty() {
    Map<String, ChangeType> changes = GitChangeClassifier.classify(null, null, null, null, null,
            null, null);

    assertThat(changes).isEmpty();
  }

  @Test
  void emptyInputsProduceEmptyMap() {
    Map<String, ChangeType> changes = GitChangeClassifier.classify(Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(), Set.of(), Set.of());

    assertThat(changes).isEmpty();
  }

  @Test
  void changedAndModifiedBothMapToModified() {
    Map<String, ChangeType> changes = GitChangeClassifier.classify(Set.of(), Set.of("a"),
            Set.of("b"), Set.of(), Set.of(), Set.of(), Set.of());

    assertThat(changes)
            .containsEntry("a", ChangeType.MODIFIED)
            .containsEntry("b", ChangeType.MODIFIED)
            .hasSize(2);
  }

  @Test
  void untrackedOverridesModified() {
    Map<String, ChangeType> changes = GitChangeClassifier.classify(Set.of(), Set.of("p"),
            Set.of("p"), Set.of("p"), Set.of(), Set.of(), Set.of());

    assertThat(changes).containsEntry("p", ChangeType.UNTRACKED);
  }

  @Test
  void addedOverridesUntrackedAndModified() {
    Map<String, ChangeType> changes = GitChangeClassifier.classify(Set.of("p"), Set.of("p"),
            Set.of("p"), Set.of("p"), Set.of(), Set.of(), Set.of());

    assertThat(changes).containsEntry("p", ChangeType.ADDED);
  }

  @Test
  void deletedOverridesAddedUntrackedAndModified() {
    Map<String, ChangeType> changes = GitChangeClassifier.classify(Set.of("p"), Set.of("p"),
            Set.of("p"), Set.of("p"), Set.of("p"), Set.of(), Set.of());

    assertThat(changes).containsEntry("p", ChangeType.DELETED);
  }

  @Test
  void deletedFromMissingOverridesAddedUntrackedAndModified() {
    Map<String, ChangeType> changes = GitChangeClassifier.classify(Set.of("p"), Set.of("p"),
            Set.of("p"), Set.of("p"), Set.of(), Set.of("p"), Set.of());

    assertThat(changes).containsEntry("p", ChangeType.DELETED);
  }

  @Test
  void conflictingOverridesDeleted() {
    Map<String, ChangeType> changes = GitChangeClassifier.classify(Set.of("p"), Set.of("p"),
            Set.of("p"), Set.of("p"), Set.of("p"), Set.of("p"), Set.of("p"));

    assertThat(changes).containsEntry("p", ChangeType.CONFLICTING);
  }

  @Test
  void conflictingOverridesAdded() {
    Map<String, ChangeType> changes = GitChangeClassifier.classify(Set.of("p"), Set.of(),
            Set.of(), Set.of(), Set.of(), Set.of(), Set.of("p"));

    assertThat(changes).containsEntry("p", ChangeType.CONFLICTING);
  }

  @Test
  void independentPathsKeepTheirOwnType() {
    Map<String, ChangeType> changes = GitChangeClassifier.classify(
            Set.of("added"),
            Set.of("changed"),
            Set.of("modified"),
            Set.of("untracked"),
            Set.of("removed"),
            Set.of("missing"),
            Set.of("conflicting"));

    assertThat(changes)
            .containsEntry("added", ChangeType.ADDED)
            .containsEntry("changed", ChangeType.MODIFIED)
            .containsEntry("modified", ChangeType.MODIFIED)
            .containsEntry("untracked", ChangeType.UNTRACKED)
            .containsEntry("removed", ChangeType.DELETED)
            .containsEntry("missing", ChangeType.DELETED)
            .containsEntry("conflicting", ChangeType.CONFLICTING)
            .hasSize(7);
  }

  @Test
  void repoStatusNullNormalisesToEmpty() {
    RepoStatus status = new RepoStatus(Path.of("/repo"), null, null);

    assertThat(status.workTree()).isEqualTo(Path.of("/repo"));
    assertThat(status.dirtyPaths()).isEmpty();
    assertThat(status.changes()).isEmpty();
    assertThat(status.changeOf("any")).isEmpty();
  }

  @Test
  void repoStatusOfDerivesDirtyPathsFromChanges() {
    RepoStatus status = RepoStatus.of(Path.of("/repo"),
            Map.of("a.java", ChangeType.ADDED, "b.java", ChangeType.MODIFIED));

    assertThat(status.dirtyPaths()).containsExactlyInAnyOrder("a.java", "b.java");
    assertThat(status.changeOf("a.java")).contains(ChangeType.ADDED);
    assertThat(status.changeOf("missing")).isEmpty();
  }

  @Test
  void repoStatusFromDirtyPathsKeepsDirtySetAndEmptyChanges() {
    RepoStatus status = RepoStatus.fromDirtyPaths(Path.of("/repo"), Set.of("a.txt", "b.txt"));

    assertThat(status.dirtyPaths()).containsExactlyInAnyOrder("a.txt", "b.txt");
    assertThat(status.changes()).isEmpty();
    assertThat(status.changeOf("a.txt")).isEmpty();
  }

  @Test
  void repoStatusTwoArgConstructorWithExplicitDirtyPaths() {
    RepoStatus status = new RepoStatus(Path.of("/repo"), Set.of("x.txt"),
            Map.of("x.txt", ChangeType.DELETED));

    assertThat(status.dirtyPaths()).containsExactly("x.txt");
    assertThat(status.changeOf("x.txt")).contains(ChangeType.DELETED);
    assertThat(status.sizes()).isEmpty();
    assertThat(status.sizeOf("x.txt")).isEmpty();
  }

  @Test
  void repoStatusCarriesSizesWhenProvided() {
    RepoStatus status = new RepoStatus(Path.of("/repo"), null,
            Map.of("a.java", ChangeType.MODIFIED), Map.of("a.java", 123L));

    assertThat(status.dirtyPaths()).containsExactly("a.java");
    assertThat(status.sizes()).containsEntry("a.java", 123L);
    assertThat(status.sizeOf("a.java")).contains(123L);
    assertThat(status.sizeOf("missing.java")).isEmpty();
  }

  @Test
  void repoStatusNullSizesNormaliseToEmpty() {
    RepoStatus status = new RepoStatus(Path.of("/repo"), null, null, null);

    assertThat(status.sizes()).isEmpty();
  }

  @Test
  void repoStatusNormalisesUriStyleWorkTree() {
    Path uriStyle = Path.of(java.net.URI.create("file:///repo"));

    RepoStatus status = new RepoStatus(uriStyle, null, Map.of("a.java", ChangeType.MODIFIED));

    assertThat(status.workTree()).isEqualTo(Path.of("/repo"));
  }

  @Test
  void jacksonRoundTripPreservesShape() throws Exception {
    Class<?> mapperClass;
    try {
      mapperClass = Class.forName("tools.jackson.databind.ObjectMapper");
    } catch (ClassNotFoundException e) {
      return;
    }

    Object mapper = mapperClass.getDeclaredConstructor().newInstance();
    Method write = mapperClass.getMethod("writeValueAsString", Object.class);
    Method read = mapperClass.getMethod("readValue", String.class, Class.class);

    RepoStatus original = RepoStatus.of(Path.of("/repo"), Map.of(
            "a.java", ChangeType.MODIFIED,
            "b.java", ChangeType.ADDED,
            "c.java", ChangeType.DELETED));
    String json = (String) write.invoke(mapper, original);
    RepoStatus roundTripped = (RepoStatus) read.invoke(mapper, json, RepoStatus.class);

    assertThat(roundTripped.workTree()).isEqualTo(original.workTree());
    assertThat(roundTripped.dirtyPaths())
            .containsExactlyInAnyOrderElementsOf(original.dirtyPaths());
    assertThat(roundTripped.changes()).isEqualTo(original.changes());
    assertThat(roundTripped.changeOf("c.java")).contains(ChangeType.DELETED);
  }

  @Test
  void jacksonRoundTripPreservesSizes() throws Exception {
    Class<?> mapperClass;
    try {
      mapperClass = Class.forName("tools.jackson.databind.ObjectMapper");
    } catch (ClassNotFoundException e) {
      return;
    }

    Object mapper = mapperClass.getDeclaredConstructor().newInstance();
    Method write = mapperClass.getMethod("writeValueAsString", Object.class);
    Method read = mapperClass.getMethod("readValue", String.class, Class.class);

    RepoStatus original = RepoStatus.of(Path.of("/repo"),
            Map.of("a.java", ChangeType.MODIFIED), Map.of("a.java", 456L));
    String json = (String) write.invoke(mapper, original);
    RepoStatus roundTripped = (RepoStatus) read.invoke(mapper, json, RepoStatus.class);

    assertThat(roundTripped.sizes()).containsEntry("a.java", 456L);
  }

  @Test
  void jacksonParsesPayloadWithoutSizes() throws Exception {
    Class<?> mapperClass;
    try {
      mapperClass = Class.forName("tools.jackson.databind.ObjectMapper");
    } catch (ClassNotFoundException e) {
      return;
    }

    Object mapper = mapperClass.getDeclaredConstructor().newInstance();
    Method read = mapperClass.getMethod("readValue", String.class, Class.class);

    String legacy = "{\"workTree\":\"/repo\",\"dirtyPaths\":[\"a.java\"],"
            + "\"changes\":{\"a.java\":\"MODIFIED\"}}";
    RepoStatus parsed = (RepoStatus) read.invoke(mapper, legacy, RepoStatus.class);

    assertThat(parsed.changes()).containsEntry("a.java", ChangeType.MODIFIED);
    assertThat(parsed.sizes()).isEmpty();
  }
}

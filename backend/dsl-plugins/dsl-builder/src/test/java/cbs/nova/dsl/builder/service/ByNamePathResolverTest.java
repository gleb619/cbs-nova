package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.builder.config.BuilderServiceConfiguration;
import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.repository.FileRepository;
import com.github.benmanes.caffeine.cache.Ticker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

class ByNamePathResolverTest {

  @TempDir
  Path workTree;

  private FileService fileService;
  private ByNamePathResolver resolver;

  @BeforeEach
  void setUp() {
    var properties = DslBuilderProperties.defaultsWithWorkspace(workTree);
    fileService = new FileService(properties, new FileRepository(),
            new FileBuffer(
                    BuilderServiceConfiguration.pendingCache(properties, Ticker.systemTicker())),
            new FileBulkhead(new Semaphore(8), new Semaphore(8), 5L),
            new WorkspaceService(properties, new GitService()),
            new NoOpGitStatusProvider());
    resolver = new ByNamePathResolver(properties, fileService);
  }

  @Test
  void resolvesSourceByDslNamingConventionInSrcDsl() throws IOException {
    write(workTree.resolve("src/dsl/LoanDisbursementDsl.java"), "step {}");

    assertThat(resolver.resolveSourcePath("LoanDisbursement"))
            .contains("src/dsl/LoanDisbursementDsl.java");
  }

  @Test
  void fallsBackToPlainJavaNameInSrcDsl() throws IOException {
    write(workTree.resolve("src/dsl/OnlyPlain.java"), "step {}");

    assertThat(resolver.resolveSourcePath("OnlyPlain")).contains("src/dsl/OnlyPlain.java");
  }

  @Test
  void prefersSrcDslOverSrcModels() throws IOException {
    write(workTree.resolve("src/models/FooDsl.java"), "model");
    write(workTree.resolve("src/dsl/Foo.java"), "dsl");

    assertThat(resolver.resolveSourcePath("Foo")).contains("src/dsl/Foo.java");
  }

  @Test
  void resolvesSourceFromSrcModels() throws IOException {
    write(workTree.resolve("src/models/BarDsl.java"), "model");

    assertThat(resolver.resolveSourcePath("Bar")).contains("src/models/BarDsl.java");
  }

  @Test
  void prefersDslSuffixWithinSameDirectory() throws IOException {
    write(workTree.resolve("src/dsl/FooDsl.java"), "dsl");
    write(workTree.resolve("src/dsl/Foo.java"), "plain");

    assertThat(resolver.resolveSourcePath("Foo")).contains("src/dsl/FooDsl.java");
  }

  @Test
  void returnsEmptyForUnknownName() {
    assertThat(resolver.resolveSourcePath("Missing")).isEmpty();
  }

  @Test
  void returnsEmptyForBlankName() {
    assertThat(resolver.resolveSourcePath(" ")).isEmpty();
  }

  @Test
  void resolvesBufferStagedSourceBeforeFlush() {
    fileService.stageWrite("src/dsl/StagedDsl.java", "step {}");

    assertThat(resolver.resolveSourcePath("Staged")).contains("src/dsl/StagedDsl.java");
  }

  @Test
  void resolvesDocByKebabCaseConvention() throws IOException {
    write(workTree.resolve("docs/batch-processing.md"), "# Batch");

    assertThat(resolver.resolveDocPath("BatchProcessing")).contains("batch-processing.md");
  }

  @Test
  void fallsBackToRawDocName() throws IOException {
    write(workTree.resolve("docs/MyDoc.md"), "# My doc");

    assertThat(resolver.resolveDocPath("MyDoc")).contains("MyDoc.md");
  }

  @Test
  void returnsEmptyDocForUnknownName() {
    assertThat(resolver.resolveDocPath("Missing")).isEmpty();
  }

  @Test
  void resolvesBufferStagedDocBeforeFlush() {
    fileService.stageWrite("docs/staged-doc.md", "# Staged");

    assertThat(resolver.resolveDocPath("StagedDoc")).contains("staged-doc.md");
  }

  @Test
  void defaultDocPathIsKebabCase() {
    assertThat(resolver.defaultDocPath("BatchProcessing")).isEqualTo("batch-processing.md");
  }

  @Test
  void resolvesNestedRecordFromModelsHolderFile() throws IOException {
    write(workTree.resolve("src/models/BatchModels.java"), """
            public class BatchModels {
              public record BatchIn(java.util.List<BatchItem> items) {
              }

              public record BatchItem(String id, int value) {
              }

              public record BatchOut(int total, String summary) {
              }
            }
            """);

    assertThat(resolver.resolveSourcePath("BatchItem")).contains("src/models/BatchModels.java");
    assertThat(resolver.resolveSourcePath("BatchOut")).contains("src/models/BatchModels.java");
  }

  @Test
  void resolvesInterfaceClassAndEnumFromModelsHolderFile() throws IOException {
    write(workTree.resolve("src/models/Holders.java"), """
            public class Holders {
              public interface PaymentPort {
              }

              public static class LegacyAdapter {
              }

              public enum Mode {
                ON, OFF
              }
            }
            """);

    assertThat(resolver.resolveSourcePath("PaymentPort")).contains("src/models/Holders.java");
    assertThat(resolver.resolveSourcePath("LegacyAdapter")).contains("src/models/Holders.java");
    assertThat(resolver.resolveSourcePath("Mode")).contains("src/models/Holders.java");
  }

  @Test
  void resolvesGenericRecordDeclaration() throws IOException {
    write(workTree.resolve("src/models/Envelope.java"), """
            public record Envelope<T>(T payload) {
            }
            """);

    assertThat(resolver.resolveSourcePath("Envelope")).contains("src/models/Envelope.java");
  }

  @Test
  void ignoresTypeNamesSharingOnlyAPrefix() throws IOException {
    write(workTree.resolve("src/models/PrefixModels.java"), """
            public class PrefixModels {
              public record BatchItemExtra(String id) {
              }
            }
            """);

    assertThat(resolver.resolveSourcePath("BatchItem")).isEmpty();
  }

  @Test
  void resolvesStagedModelsHolderBeforeFlush() {
    fileService.stageWrite("src/models/StagedModels.java", """
            public class StagedModels {
              public record StagedThing(String id) {
              }
            }
            """);

    assertThat(resolver.resolveSourcePath("StagedThing")).contains("src/models/StagedModels.java");

    fileService.flushPending();

    assertThat(resolver.resolveSourcePath("StagedThing")).contains("src/models/StagedModels.java");
  }

  @Test
  void reResolvesSourcesAfterBufferChange() {
    assertThat(resolver.resolveSourcePath("LateThing")).isEmpty();

    fileService.stageWrite("src/models/LateModels.java", """
            public class LateModels {
              public record LateThing(String id) {
              }
            }
            """);

    assertThat(resolver.resolveSourcePath("LateThing")).contains("src/models/LateModels.java");
  }

  @Test
  void resolvesDocByFrontmatterName() throws IOException {
    write(workTree.resolve("docs/batch-flow.md"), """
            ---
            name: BatchProcessing
            description: Sums the batch.
            ---

            # BatchProcessing
            """);

    assertThat(resolver.resolveDocPath("BatchProcessing")).contains("batch-flow.md");
  }

  @Test
  void resolvesDocByQuotedFrontmatterName() throws IOException {
    write(workTree.resolve("docs/quoted.md"), """
            ---
            name: "Quoted Name"
            ---
            """);

    assertThat(resolver.resolveDocPath("Quoted Name")).contains("quoted.md");
  }

  @Test
  void skipsDocsWithoutParseableFrontmatter() throws IOException {
    write(workTree.resolve("docs/decoy.md"), """
            # Decoy

            name: Ghost appears only in the body text.
            """);

    assertThat(resolver.resolveDocPath("Ghost")).isEmpty();
  }

  @Test
  void prefersDirectDocFileNameOverFrontmatterMatch() throws IOException {
    write(workTree.resolve("docs/batch-processing.md"), "# direct hit");
    write(workTree.resolve("docs/other.md"), """
            ---
            name: BatchProcessing
            ---
            """);

    assertThat(resolver.resolveDocPath("BatchProcessing")).contains("batch-processing.md");
  }

  @Test
  void resolvesStagedDocWithMismatchedFileNameBeforeFlush() {
    fileService.stageWrite("docs/renamed.md", """
            ---
            name: StagedDoc
            ---

            # StagedDoc
            """);

    assertThat(resolver.resolveDocPath("StagedDoc")).contains("renamed.md");

    fileService.flushPending();

    assertThat(resolver.resolveDocPath("StagedDoc")).contains("renamed.md");
  }

  @Test
  void reResolvesDocsAfterBufferChange() {
    assertThat(resolver.resolveDocPath("LateDoc")).isEmpty();

    fileService.stageWrite("docs/late.md", """
            ---
            name: LateDoc
            ---
            """);

    assertThat(resolver.resolveDocPath("LateDoc")).contains("late.md");
  }

  private static void write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  private static final class NoOpGitStatusProvider
          implements
            ObjectProvider<GitStatusService> {

    @Override
    public GitStatusService getIfAvailable() {
      return null;
    }

    @Override
    public GitStatusService getIfUnique() {
      return null;
    }
  }
}

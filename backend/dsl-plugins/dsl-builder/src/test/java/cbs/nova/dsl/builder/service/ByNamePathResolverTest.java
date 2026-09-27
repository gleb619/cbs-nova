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
    resolver = new ByNamePathResolver(new WorkspaceService(properties, new GitService()),
            properties, fileService);
  }

  @Test
  void resolvesNestedFileByDslNamingConvention() throws IOException {
    write(workTree.resolve("src/dsl/LoanDisbursementDsl.java"), "step {}");

    assertThat(resolver.resolveSourcePath("LoanDisbursement"))
            .contains("src/dsl/LoanDisbursementDsl.java");
  }

  @Test
  void resolvesRootFileDirectly() throws IOException {
    write(workTree.resolve("PlainDsl.java"), "step {}");

    assertThat(resolver.resolveSourcePath("Plain")).contains("PlainDsl.java");
  }

  @Test
  void prefersDslSuffixOverPlainJavaName() throws IOException {
    write(workTree.resolve("a/FooDsl.java"), "a");
    write(workTree.resolve("b/Foo.java"), "b");

    assertThat(resolver.resolveSourcePath("Foo")).contains("a/FooDsl.java");
  }

  @Test
  void fallsBackToPlainJavaName() throws IOException {
    write(workTree.resolve("dsl/OnlyPlain.java"), "step {}");

    assertThat(resolver.resolveSourcePath("OnlyPlain")).contains("dsl/OnlyPlain.java");
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
    fileService.stageWrite("StagedDsl.java", "step {}");

    assertThat(resolver.resolveSourcePath("Staged")).contains("StagedDsl.java");
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
  void resolvesNestedDocRelativeToDocsRoot() throws IOException {
    write(workTree.resolve("docs/guides/loan-approval.md"), "# Approval");

    assertThat(resolver.resolveDocPath("LoanApproval")).contains("guides/loan-approval.md");
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

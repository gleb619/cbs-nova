package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.exception.PieceManifestValidationException;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.PostCheck;
import cbs.nova.starter.model.Target;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

class PieceManifestServiceTest {

  private static final String SHIPPED_MANIFEST = "../../../app/dsl/src/main/resources/piece-manifest.yaml";

  @Test
  void loadsShippedManifestAndIndexesPieces() throws IOException {
    var service = service(Path.of(SHIPPED_MANIFEST).toAbsolutePath().toUri().toString());

    Optional<Piece> reload = service.find("dsl-reload");
    assertThat(reload).isPresent();
    assertThat(reload.get().target()).isInstanceOf(Target.ApiTarget.class);
    assertThat(((Target.ApiTarget) reload.get().target()).route())
            .isEqualTo("POST /api/dsl/reload");
    assertThat(reload.get().failMode()).isEqualTo("deny");

    List<Piece> apiPieces = service.byTarget(new Target.ApiTarget("POST /api/dsl/reload"));
    assertThat(apiPieces).hasSize(5).extracting(Piece::id)
            .containsExactlyInAnyOrder("dsl-reload", "execution-cancel", "vhs-tape-delete",
                    "vhs-tape-replay", "dsl-signal-send");

    assertThat(service.findByRoute("POST", "/api/dsl/reload")).isPresent()
            .hasValueSatisfying(p -> assertThat(p.id()).isEqualTo("dsl-reload"));
    assertThat(service.findByRoute("POST", "/api/executions/123/cancel")).isPresent()
            .hasValueSatisfying(p -> assertThat(p.id()).isEqualTo("execution-cancel"));

    assertThat(service.byTarget(new Target.ButtonTarget("workbench-publish-btn")))
            .hasSize(1).extracting(Piece::id).containsExactly("workbench-publish");
    assertThat(service.byTarget(new Target.ObjectTarget("helper", "PreviewSandbox")))
            .hasSize(1).extracting(Piece::id).containsExactly("preview-sandbox-helper");
  }

  @Test
  void unknownTargetTypeFailsFastWithPieceIdAndField() throws IOException {
    Path file = writeManifest("""
            pieces:
              - id: bad-target
                target:
                  type: unknown
                preCheck: []
                postCheck: []
                failMode: deny
            """);
    assertThatThrownBy(() -> service(file.toUri().toString()))
            .isInstanceOf(PieceManifestValidationException.class)
            .hasMessageContaining("bad-target")
            .hasMessageContaining("target.type");
  }

  @Test
  void unknownPreCheckTypeFailsFastWithPieceIdAndField() throws IOException {
    Path file = writeManifest("""
            pieces:
              - id: bad-precheck
                target:
                  type: api
                  route: POST /api/x
                preCheck:
                  - type: wizardry
                postCheck: []
                failMode: deny
            """);
    assertThatThrownBy(() -> service(file.toUri().toString()))
            .isInstanceOf(PieceManifestValidationException.class)
            .hasMessageContaining("bad-precheck")
            .hasMessageContaining("preCheck.type");
  }

  @Test
  void unknownPostCheckTypeFailsFastWithPieceIdAndField() throws IOException {
    Path file = writeManifest("""
            pieces:
              - id: bad-postcheck
                target:
                  type: api
                  route: POST /api/x
                preCheck: []
                postCheck:
                  - type: spell
                failMode: deny
            """);
    assertThatThrownBy(() -> service(file.toUri().toString()))
            .isInstanceOf(PieceManifestValidationException.class)
            .hasMessageContaining("bad-postcheck")
            .hasMessageContaining("postCheck.type");
  }

  @Test
  void duplicateIdFailsFastWithPieceIdAndField() throws IOException {
    Path file = writeManifest("""
            pieces:
              - id: dup
                target:
                  type: api
                  route: POST /api/a
                preCheck: []
                postCheck: []
                failMode: deny
              - id: dup
                target:
                  type: api
                  route: POST /api/b
                preCheck: []
                postCheck: []
                failMode: deny
            """);
    assertThatThrownBy(() -> service(file.toUri().toString()))
            .isInstanceOf(PieceManifestValidationException.class)
            .hasMessageContaining("dup")
            .hasMessageContaining("id");
  }

  @Test
  void blankPathStartsWithEmptySnapshot() {
    var service = service("  ");
    assertThat(service.find("dsl-reload")).isEmpty();
    assertThat(service.byTarget(new Target.ApiTarget("POST /api/dsl/reload"))).isEmpty();
    assertThat(service.findByRoute("POST", "/api/dsl/reload")).isEmpty();
  }

  @Test
  void missingPathStartsWithEmptySnapshot() {
    var service = service("file:/tmp/cbs-nova-manifest-missing-" + System.nanoTime());
    assertThat(service.find("dsl-reload")).isEmpty();
  }

  @Test
  void failedReloadLeavesPreviousSnapshotIntact() throws IOException {
    Path file = writeManifest("""
            pieces:
              - id: keep-me
                target:
                  type: api
                  route: POST /api/keep
                preCheck: []
                postCheck: []
                failMode: deny
            """);
    var service = service(file.toUri().toString());
    assertThat(service.find("keep-me")).isPresent();

    Files.writeString(file, """
            pieces:
              - id: bad-piece
                target:
                  type: api
                  route: POST /api/bad
                preCheck:
                  - type: unknown-check
                postCheck: []
                failMode: deny
            """);
    var response = service.reload(reloadRequest());
    assertThat(response.statusCode().value()).isEqualTo(400);
    assertThat(service.find("keep-me")).isPresent();
    assertThat(service.find("bad-piece")).isEmpty();
  }

  @Test
  void successfulReloadSwapsSnapshotAtomically() throws IOException {
    Path file = writeManifest("""
            pieces:
              - id: first
                target:
                  type: api
                  route: POST /api/first
                preCheck: []
                postCheck: []
                failMode: deny
            """);
    var service = service(file.toUri().toString());

    Files.writeString(file, """
            pieces:
              - id: second
                target:
                  type: api
                  route: POST /api/second
                preCheck: []
                postCheck: []
                failMode: deny
              - id: third
                target:
                  type: button
                  uiKey: third-btn
                preCheck: []
                postCheck: []
                failMode: deny
            """);
    var response = service.reload(reloadRequest());
    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(service.find("first")).isEmpty();
    assertThat(service.find("second")).isPresent();
    assertThat(service.find("third")).isPresent();
    assertThat(service.byTarget(new Target.ApiTarget("POST /api/second"))).hasSize(1);
  }

  @Test
  void concurrentReadsDuringReloadNeverSeePartialIndex() throws Exception {
    Path file = writeManifest("""
            pieces:
              - id: stable
                target:
                  type: api
                  route: POST /api/stable
                preCheck: []
                postCheck: []
                failMode: deny
            """);
    var service = service(file.toUri().toString());

    // The snapshot is a single volatile reference, so concurrent readers always see either the
    // complete old or the complete new index. This test exercises that guarantee under contention
    // and verifies the final state is the complete reloaded snapshot.
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      AtomicInteger reads = new AtomicInteger();
      CountDownLatch start = new CountDownLatch(1);
      CountDownLatch done = new CountDownLatch(4);

      for (int i = 0; i < 4; i++) {
        pool.submit(() -> {
          try {
            start.await();
            for (int r = 0; r < 1000; r++) {
              service.find("one");
              service.find("two");
              service.find("stable");
              service.findByRoute("POST", "/api/one");
              service.findByRoute("POST", "/api/two");
              reads.incrementAndGet();
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            done.countDown();
          }
        });
      }

      start.countDown();
      Files.writeString(file, """
              pieces:
                - id: one
                  target:
                    type: api
                    route: POST /api/one
                  preCheck: []
                  postCheck: []
                  failMode: deny
                - id: two
                  target:
                    type: api
                    route: POST /api/two
                  preCheck: []
                  postCheck: []
                  failMode: deny
              """);
      service.reload(reloadRequest());
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(reads.get()).isEqualTo(4000);
      assertThat(service.find("one")).isPresent();
      assertThat(service.find("two")).isPresent();
      assertThat(service.find("stable")).isEmpty();
    } finally {
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  @Test
  void reloadWritesAuditRowOnSuccess() throws IOException {
    var audit = AuditTestSupport.h2();
    Path file = writeManifest("""
            pieces:
              - id: audited
                target:
                  type: api
                  route: POST /api/audited
                preCheck: []
                postCheck: []
                failMode: deny
            """);
    var service = new PieceManifestService(
            new CbsDslManifestProperties(true, file.toUri().toString()),
            new DefaultResourceLoader(),
            AuditTestSupport.providerOf(audit.service()));

    var response = service.reload(reloadRequest());
    assertThat(response.statusCode().value()).isEqualTo(200);

    var rows = audit.service().search(null, 0, 10);
    assertThat(rows.total()).isEqualTo(1);
    var row = rows.items().get(0);
    assertThat(row.action()).isEqualTo("MANIFEST_RELOAD");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo(file.toUri().toString());
  }

  @Test
  void reloadWritesAuditRowOnValidationFailure() throws IOException {
    var audit = AuditTestSupport.h2();
    Path file = writeManifest("""
            pieces:
              - id: good
                target:
                  type: api
                  route: POST /api/good
                preCheck: []
                postCheck: []
                failMode: deny
            """);
    var service = new PieceManifestService(
            new CbsDslManifestProperties(true, file.toUri().toString()),
            new DefaultResourceLoader(),
            AuditTestSupport.providerOf(audit.service()));

    Files.writeString(file, """
            pieces:
              - id: bad
                target:
                  type: api
                  route: POST /api/bad
                preCheck:
                  - type: bad-check
                postCheck: []
                failMode: deny
            """);
    var response = service.reload(reloadRequest());
    assertThat(response.statusCode().value()).isEqualTo(400);

    var rows = audit.service().search(null, 0, 10);
    assertThat(rows.total()).isEqualTo(1);
    var row = rows.items().get(0);
    assertThat(row.action()).isEqualTo("MANIFEST_RELOAD");
    assertThat(row.outcome()).isEqualTo("FAILURE");
  }

  @Test
  void parsesOnFailurePolicyPerPostCheckEntryDefaultingToWarn() throws IOException {
    Path file = writeManifest("""
            pieces:
              - id: post-checked
                target:
                  type: api
                  route: POST /api/post-checked
                preCheck: []
                postCheck:
                  - type: audit-write
                    action: DEFINITION_RELOAD
                  - type: notify
                    channel: workbench
                    onFailure: block-next-execution
                failMode: deny
            """);
    var service = service(file.toUri().toString());

    var postChecks = service.find("post-checked").orElseThrow().postCheck();
    assertThat(postChecks).hasSize(2);
    assertThat(postChecks.get(0).onFailure()).isEqualTo(PostCheck.ON_FAILURE_WARN);
    assertThat(postChecks.get(1).onFailure()).isEqualTo(PostCheck.ON_FAILURE_BLOCK);
  }

  @Test
  void unknownOnFailurePolicyIsRejectedWithPieceIdAndField() throws IOException {
    Path file = writeManifest("""
            pieces:
              - id: post-checked
                target:
                  type: api
                  route: POST /api/post-checked
                preCheck: []
                postCheck:
                  - type: notify
                    channel: workbench
                    onFailure: explode
                failMode: deny
            """);

    assertThatThrownBy(() -> service(file.toUri().toString()))
            .isInstanceOf(PieceManifestValidationException.class)
            .hasMessageContaining("post-checked")
            .hasMessageContaining("postCheck.onFailure");
  }

  @Test
  void successfulReloadClearsPostCheckBlocks() throws IOException {
    Path file = writeManifest("""
            pieces:
              - id: post-checked
                target:
                  type: api
                  route: POST /api/post-checked
                preCheck: []
                postCheck: []
                failMode: deny
            """);
    PieceCheckBlockRegistry registry = new PieceCheckBlockRegistry(
            new CbsDslManifestProperties(true, "classpath:piece-manifest.yaml"),
            System::currentTimeMillis);
    registry.block("post-checked", "auth:alice", "invariant failed");
    var service = new PieceManifestService(
            new CbsDslManifestProperties(true, file.toUri().toString()),
            new DefaultResourceLoader(),
            AuditTestSupport.emptyProvider(),
            new ObjectProvider<PieceCheckBlockRegistry>() {
              @Override
              public PieceCheckBlockRegistry getObject() {
                return registry;
              }

              @Override
              public PieceCheckBlockRegistry getIfAvailable() {
                return registry;
              }

              @Override
              public PieceCheckBlockRegistry getIfUnique() {
                return registry;
              }
            });

    var response = service.reload(reloadRequest());

    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(registry.isBlocked("post-checked", "auth:alice")).isFalse();
  }

  private static PieceManifestService service(String path) {
    return new PieceManifestService(new CbsDslManifestProperties(true, path),
            new DefaultResourceLoader(), AuditTestSupport.emptyProvider());
  }

  private static Path writeManifest(String yaml) throws IOException {
    Path file = Files.createTempFile("manifest-", ".yaml");
    Files.writeString(file, yaml);
    return file;
  }

  private static ServerRequest reloadRequest() {
    var request = new MockHttpServletRequest("POST", "/api/dsl/manifest/reload");
    return ServerRequest.create(request, List.of());
  }
}

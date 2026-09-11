package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.helper.CompensationTrackerHelper;
import cbs.nova.starter.helper.UnreliableApiHelper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Doc-drift guard (T380): fails if any helper name the runtime registry serves is not documented in
 * {@code docs/dsl/helpers.md}. Prevents a recurrence of the T350-T358 drift where nine helpers
 * shipped with zero documentation (fixed by T378).
 *
 * <p>
 * Boot pattern mirrors {@link IntermediateDslExamplesTest}: a plain {@link GlobalManager} with the
 * generated SPI resolver — no Spring context. Test-support helpers that intentionally have no
 * user-facing docs are listed in {@link #ALLOWED_UNDOCUMENTED} with a justification each.
 */
class HelperDocsCoverageTest {

  /**
   * Module test working dir is {@code backend/dsl-starter/starter}; repo root is three levels up
   * ({@code dsl-starter} → {@code backend} → repo root).
   */
  private static final Path HELPERS_DOC = Path.of("").toAbsolutePath()
          .normalize()
          .getParent().getParent().getParent()
          .resolve("docs/dsl/helpers.md");

  /**
   * Test-support helpers that genuinely register at runtime but exist only as fixtures for example
   * DSLs / tests, so they are intentionally absent from the user-facing cookbook.
   */
  private static final Set<String> ALLOWED_UNDOCUMENTED = Set.of(
          // test fixture: simulates a flaky downstream API for the HttpResilience example/tests
          "unreliableApi",
          // test fixture: fails execution only while its latch condition is armed
          "conditionalFailing",
          // test fixture: file-based latch used to synchronize/verify steps across executions
          "fileLatch",
          // test fixture: records compensation callbacks for saga/transaction tests
          "compensationTracker");

  @BeforeEach
  void bootRegistry() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(instantiatingResolver());
    GlobalManager.globalManager().registerHelperResolvers();
  }

  @AfterEach
  void cleanup() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void everyRegisteredHelperIsDocumentedInHelpersMd() throws IOException {
    assumeTrue(Files.exists(HELPERS_DOC),
            () -> "docs/dsl/helpers.md not found at " + HELPERS_DOC
                    + " (running outside the repo layout); skipping doc-drift check");

    String doc = Files.readString(HELPERS_DOC).toLowerCase(Locale.ROOT);
    List<String> registeredNames = GlobalManager.globalManager().helperNames();

    List<String> missing = registeredNames.stream()
            .filter(name -> !ALLOWED_UNDOCUMENTED.contains(name))
            .filter(name -> !doc.contains(name.toLowerCase(Locale.ROOT)))
            .toList();

    assertThat(missing)
            .as("Registered helper(s) not documented in docs/dsl/helpers.md"
                    + " (registered names: %s)"
                    + " — add a section to docs/dsl/helpers.md"
                    + " or add the name to ALLOWED_UNDOCUMENTED with justification",
                    registeredNames)
            .isEmpty();
  }

  private static HelperInstanceResolver instantiatingResolver() {
    return helperClass -> {
      if (helperClass.equals(UnreliableApiHelper.class)) {
        return new UnreliableApiHelper(Caffeine.newBuilder()
                .expireAfterWrite(StarterConstants.UNRELIABLE_API_TTL)
                .maximumSize(StarterConstants.UNRELIABLE_API_MAX_SIZE)
                .build());
      }
      if (helperClass.equals(CompensationTrackerHelper.class)) {
        return new CompensationTrackerHelper(Caffeine.newBuilder()
                .expireAfterWrite(StarterConstants.COMPENSATION_TRACKER_TTL)
                .maximumSize(StarterConstants.COMPENSATION_TRACKER_MAX_SIZE)
                .build());
      }
      try {
        var constructor = helperClass.getDeclaredConstructor();
        if (!constructor.canAccess(null)) {
          constructor.setAccessible(true);
        }
        return (Executable<?, ?>) constructor.newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName(), e);
      }
    };
  }
}

package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.SemverIn;
import cbs.nova.starter.helper.model.SemverOut;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SemverHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final SemverHelper helper = new SemverHelper();

  // --- parse ---

  @Test
  void parseSimple() {
    Map<String, Object> result = parse("1.2.3");
    assertThat(result.get("major")).isEqualTo(1);
    assertThat(result.get("minor")).isEqualTo(2);
    assertThat(result.get("patch")).isEqualTo(3);
    assertThat(result.get("preRelease")).isNull();
    assertThat(result.get("build")).isNull();
  }

  @Test
  void parseWithPrereleaseAndBuild() {
    Map<String, Object> result = parse("1.2.3-rc.1+build.5");
    assertThat(result.get("major")).isEqualTo(1);
    assertThat(result.get("minor")).isEqualTo(2);
    assertThat(result.get("patch")).isEqualTo(3);
    assertThat(result.get("preRelease")).isEqualTo("rc.1");
    assertThat(result.get("build")).isEqualTo("build.5");
  }

  @Test
  void parseStripsLeadingV() {
    Map<String, Object> result = parse("v1.2.3");
    assertThat(result.get("major")).isEqualTo(1);
    assertThat(result.get("minor")).isEqualTo(2);
    assertThat(result.get("patch")).isEqualTo(3);
    assertThat(result.get("preRelease")).isNull();
    assertThat(result.get("build")).isNull();
  }

  @Test
  void parseLeadingZeroFails() {
    Result<SemverOut> result = execute(new SemverIn("parse", "1.02.3", null, null, null, null,
            null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void parseNonVersionFails() {
    Result<SemverOut> result = execute(new SemverIn("parse", "not-a-version", null, null, null,
            null, null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // --- compare ---

  @Test
  void compareLessThan() {
    assertThat((Integer) compare("1.0.0", "2.0.0")).isEqualTo(-1);
  }

  @Test
  void compareGreaterThan() {
    assertThat((Integer) compare("2.0.0", "1.0.0")).isEqualTo(1);
  }

  @Test
  void compareEqual() {
    assertThat((Integer) compare("1.0.0", "1.0.0")).isEqualTo(0);
  }

  @Test
  void compareReleaseHigherThanPrerelease() {
    assertThat((Integer) compare("1.0.0", "1.0.0-alpha")).isEqualTo(1);
  }

  @Test
  void compareLargerPrereleaseSetHigherPrecedence() {
    assertThat((Integer) compare("1.0.0-alpha", "1.0.0-alpha.1")).isEqualTo(-1);
  }

  @Test
  void compareNumericLowerThanAlphanumericPrerelease() {
    assertThat((Integer) compare("1.0.0-alpha.1", "1.0.0-alpha.beta")).isEqualTo(-1);
  }

  @Test
  void compareAlphanumericLexically() {
    assertThat((Integer) compare("1.0.0-alpha", "1.0.0-beta")).isEqualTo(-1);
  }

  @Test
  void compareBuildMetadataIgnored() {
    assertThat((Integer) compare("1.0.0+build1", "1.0.0+build2")).isEqualTo(0);
  }

  // --- satisfies ---

  @Test
  void satisfiesCaretWithinMajor() {
    assertThat((Boolean) satisfies("1.2.5", "^1.2.3")).isTrue();
  }

  @Test
  void satisfiesCaretExcludesNextMajor() {
    assertThat((Boolean) satisfies("2.0.0", "^1.2.3")).isFalse();
  }

  @Test
  void satisfiesCaretWithZeroMajorKeepsMinor() {
    assertThat((Boolean) satisfies("0.2.5", "^0.2.3")).isTrue();
  }

  @Test
  void satisfiesCaretWithZeroMajorExcludesNextMinor() {
    assertThat((Boolean) satisfies("0.3.0", "^0.2.3")).isFalse();
  }

  @Test
  void satisfiesTildeWithinMinor() {
    assertThat((Boolean) satisfies("1.2.9", "~1.2.3")).isTrue();
  }

  @Test
  void satisfiesTildeExcludesNextMinor() {
    assertThat((Boolean) satisfies("1.3.0", "~1.2.3")).isFalse();
  }

  @Test
  void satisfiesWildcardMajor() {
    assertThat((Boolean) satisfies("1.5.0", "1.x")).isTrue();
  }

  @Test
  void satisfiesWildcardMajorExcludesOtherMajor() {
    assertThat((Boolean) satisfies("2.0.0", "1.x")).isFalse();
  }

  @Test
  void satisfiesGreaterOrEqualInclusive() {
    assertThat((Boolean) satisfies("1.2.3", ">=1.2.3")).isTrue();
  }

  @Test
  void satisfiesGreaterOrEqualExclusive() {
    assertThat((Boolean) satisfies("1.2.2", ">=1.2.3")).isFalse();
  }

  // --- bump ---

  @Test
  void bumpMajor() {
    assertThat(bump("1.2.3", "major")).isEqualTo("2.0.0");
  }

  @Test
  void bumpMinor() {
    assertThat(bump("1.2.3", "minor")).isEqualTo("1.3.0");
  }

  @Test
  void bumpPatch() {
    assertThat(bump("1.2.3", "patch")).isEqualTo("1.2.4");
  }

  @Test
  void bumpPreReleaseIncrementsLastNumericIdentifier() {
    assertThat(bump("1.2.3-rc.1", "preRelease")).isEqualTo("1.2.3-rc.2");
  }

  @Test
  void bumpPreReleaseAppendsDotOneWhenNoNumericIdentifier() {
    assertThat(bump("1.2.3-rc", "preRelease")).isEqualTo("1.2.3-rc.1");
  }

  @Test
  void bumpPreReleaseOnReleaseVersionFails() {
    Result<SemverOut> result = execute(new SemverIn("bump", "1.2.3", null, null, null,
            "preRelease", null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void bumpUnknownTypeFails() {
    Result<SemverOut> result = execute(new SemverIn("bump", "1.2.3", null, null, null,
            "unknown", null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // --- format ---

  @Test
  void formatPlain() {
    assertThat(format(1, 2, 3, null, null)).isEqualTo("1.2.3");
  }

  @Test
  void formatWithPrereleaseAndBuild() {
    assertThat(format(1, 2, 3, "rc.1", "build.5")).isEqualTo("1.2.3-rc.1+build.5");
  }

  @Test
  void formatNegativeMajorFails() {
    Result<SemverOut> result = execute(new SemverIn("format", null, null, null, null, null,
            -1, 0, 0, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
  }

  // --- unknown mode ---

  @Test
  void unknownModeFails() {
    Result<SemverOut> result = execute(new SemverIn("frobnicate", null, null, null, null, null,
            null, null, null, null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause())
            .hasMessage(
                    "semver.mode must be one of parse, compare, satisfies, bump, format,"
                            + " was: frobnicate");
  }

  // --- helpers ---

  @SuppressWarnings("unchecked")
  private Map<String, Object> parse(String version) {
    Result<SemverOut> result = execute(new SemverIn("parse", version, null, null, null, null,
            null, null, null, null, null));
    assertThat(result.isSuccess()).isTrue();
    return (Map<String, Object>) result.value().result();
  }

  private Object compare(String a, String b) {
    Result<SemverOut> result = execute(new SemverIn("compare", null, a, b, null, null,
            null, null, null, null, null));
    assertThat(result.isSuccess()).isTrue();
    return result.value().result();
  }

  private Object satisfies(String version, String range) {
    Result<SemverOut> result = execute(new SemverIn("satisfies", version, null, null, range, null,
            null, null, null, null, null));
    assertThat(result.isSuccess()).isTrue();
    return result.value().result();
  }

  private String bump(String version, String bumpType) {
    Result<SemverOut> result = execute(new SemverIn("bump", version, null, null, null, bumpType,
            null, null, null, null, null));
    assertThat(result.isSuccess()).isTrue();
    return (String) result.value().result();
  }

  private String format(int major, int minor, int patch, String preRelease, String build) {
    Result<SemverOut> result = execute(new SemverIn("format", null, null, null, null, null,
            major, minor, patch, preRelease, build));
    assertThat(result.isSuccess()).isTrue();
    return (String) result.value().result();
  }

  private Result<SemverOut> execute(SemverIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}

package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.UuidV7In;
import cbs.nova.starter.helper.model.UuidV7Out;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.StringLength;

class UuidV7HelperPropertyTest {
  private final ContextFactory contextFactory = new ContextFactory();
  private final UuidV7Helper helper = new UuidV7Helper();

  private String generate(String namespace) {
    return helper
            .execute(contextFactory.of(new UuidV7In(namespace), ExecutionMode.PREVIEW))
            .value()
            .uuid();
  }

  @Property(tries = 1000)
  void formatMatchesV7Regex(@ForAll @StringLength(max = 40) String namespace) {
    String uuid = generate(namespace);
    assertThat(uuid)
            .matches("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
  }

  @Property(tries = 1000)
  void lengthIs36(@ForAll @StringLength(max = 40) String namespace) {
    assertThat(generate(namespace).length()).isEqualTo(36);
  }

  @Property(tries = 1000)
  void versionNibbleIs7(@ForAll @StringLength(max = 40) String namespace) {
    assertThat(generate(namespace).charAt(14)).isEqualTo('7');
  }

  @Property(tries = 1000)
  void variantNibbleIsIn89ab(@ForAll @StringLength(max = 40) String namespace) {
    assertThat("89ab".indexOf(generate(namespace).charAt(19))).isGreaterThanOrEqualTo(0);
  }

  @Property(tries = 1000)
  void monotonicWithinRun(@ForAll @StringLength(max = 40) String namespace) {
    String first = generate(namespace);
    String second = generate(namespace);
    assertThat(first.compareTo(second)).isLessThanOrEqualTo(0);
  }

  @Property(tries = 1000)
  void namespaceTailIsDeterministic(@ForAll @StringLength(max = 40) String namespace) {
    Assume.that(!namespace.isBlank());
    String tail = generate(namespace).substring(24);
    assertThat(generate(namespace).substring(24)).isEqualTo(tail);
    assertThat(generate(namespace + "-different").substring(24)).isNotEqualTo(tail);
  }
}

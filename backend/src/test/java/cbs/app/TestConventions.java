package cbs.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "cbs.app")
public class TestConventions {

  @ArchTest
  static final ArchRule testMethodsMustHaveDisplayName = methods()
      .that()
      .areAnnotatedWith(Test.class)
      .should()
      .beAnnotatedWith(DisplayName.class)
      .because("Every test method must have a @DisplayName for readability");

  @ArchTest
  static final ArchRule testMethodsMustStartWithShould = methods()
      .that()
      .areAnnotatedWith(Test.class)
      .should(new HaveNameMatchingCondition("^should[A-Z][a-zA-Z0-9_]*$"))
      .because("Test methods must follow the 'shouldXxx' naming convention");

  public static class HaveNameMatchingCondition extends ArchCondition<JavaMethod> {

    private final String pattern;

    public HaveNameMatchingCondition(String pattern) {
      super("have name matching " + pattern);
      this.pattern = pattern;
    }

    @Override
    public void check(JavaMethod method, ConditionEvents events) {
      boolean matches = method.getName().matches(pattern);
      String message = String.format(
          "Method '%s' in '%s' does not match pattern '%s'",
          method.getName(), method.getOwner().getName(), pattern);
      events.add(new SimpleConditionEvent(method, matches, message));
    }
  }
}

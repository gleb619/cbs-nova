package cbs.nova.dsl.idea.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

class DslProjectStateServiceTest extends BasePlatformTestCase {

  public void testFreshProjectIsNotActiveDslProject() {
    var service = DslProjectStateService.getInstance(getProject());

    assertThat(service.isActiveDslProject()).isFalse();
  }

  public void testSetActiveDslProjectRoundTripsTrueAndFalse() {
    var service = DslProjectStateService.getInstance(getProject());

    service.setActiveDslProject(true);
    assertThat(service.isActiveDslProject()).isTrue();

    service.setActiveDslProject(false);
    assertThat(service.isActiveDslProject()).isFalse();
  }

  public void testGetInstanceReturnsSameInstanceForSameProject() {
    var first = DslProjectStateService.getInstance(getProject());
    var second = DslProjectStateService.getInstance(getProject());

    assertThat(second).isSameAs(first);
  }

  public void testFlippingFlagFromFreshStateYieldsPersistedValue() {
    var service = DslProjectStateService.getInstance(getProject());
    assertThat(service.isActiveDslProject()).isFalse();

    service.setActiveDslProject(true);

    assertThat(DslProjectStateService.getInstance(getProject()).isActiveDslProject())
            .isTrue();
  }
}

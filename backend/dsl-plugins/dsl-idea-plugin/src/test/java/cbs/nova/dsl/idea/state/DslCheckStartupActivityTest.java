package cbs.nova.dsl.idea.state;

import static org.assertj.core.api.Assertions.assertThat;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

class DslCheckStartupActivityTest extends BasePlatformTestCase {

  public void testMarksActiveWhenBothDslAndModelsDirsExist() {
    myFixture.addFileToProject("src/dsl/SimpleGreetingDsl.java", "define stuff");
    myFixture.addFileToProject("src/models/GreetingModels.java", "record stuff");

    new DslCheckStartupActivity().execute(getProject(), (Continuation<? super Unit>) null);

    assertThat(DslProjectStateService.getInstance(getProject()).isActiveDslProject()).isTrue();
  }

  public void testInactiveWhenDslDirMissing() {
    myFixture.addFileToProject("src/models/GreetingModels.java", "record stuff");

    new DslCheckStartupActivity().execute(getProject(), (Continuation<? super Unit>) null);

    assertThat(DslProjectStateService.getInstance(getProject()).isActiveDslProject()).isFalse();
  }

  public void testInactiveWhenModelsDirMissing() {
    myFixture.addFileToProject("src/dsl/SimpleGreetingDsl.java", "define stuff");

    new DslCheckStartupActivity().execute(getProject(), (Continuation<? super Unit>) null);

    assertThat(DslProjectStateService.getInstance(getProject()).isActiveDslProject()).isFalse();
  }

  public void testInactiveOnFreshProjectWithNoDirs() {
    new DslCheckStartupActivity().execute(getProject(), (Continuation<? super Unit>) null);

    assertThat(DslProjectStateService.getInstance(getProject()).isActiveDslProject()).isFalse();
  }

  public void testReRunningOverwritesPreviousFlag() {
    var service = DslProjectStateService.getInstance(getProject());
    service.setActiveDslProject(true);

    new DslCheckStartupActivity().execute(getProject(), (Continuation<? super Unit>) null);

    assertThat(service.isActiveDslProject()).isFalse();
  }
}

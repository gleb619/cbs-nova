package cbs.nova.temporal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.SpecDefinitionRegistry;
import io.temporal.activity.ActivityOptions;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class ActivityManagerTest {

  @Mock
  private SpecDefinitionRegistry artifactRegistry;

  @InjectMocks
  private ActivityManager activityManager;

  interface SampleActivity {
    String execute(String input);
  }

  @BeforeEach
  void setUp() {
    when(artifactRegistry.getActivityInterface("SAMPLE_TX")).thenReturn(SampleActivity.class);
  }

  @Test
  @DisplayName("shouldThrowWhenActivityInterfaceDoesNotMatch")
  void shouldThrowWhenActivityInterfaceDoesNotMatch() {
    when(artifactRegistry.getActivityInterface("SAMPLE_TX")).thenReturn(SampleActivity.class);

    IllegalArgumentException ex = assertThrows(
        IllegalArgumentException.class,
        () -> activityManager.newActivityStub("SAMPLE_TX", Runnable.class, defaultOptions()));

    assertThat(ex.getMessage())
        .contains("SAMPLE_TX")
        .contains("SampleActivity")
        .contains("Runnable");
  }

  @Test
  @DisplayName("shouldReturnActivityStubWhenInterfaceMatches")
  void shouldReturnActivityStubWhenInterfaceMatches() {
    SampleActivity expectedStub = mock(SampleActivity.class);
    when(artifactRegistry.getActivityInterface("SAMPLE_TX")).thenReturn(SampleActivity.class);

    try (MockedStatic<Workflow> workflow = Mockito.mockStatic(Workflow.class)) {
      workflow
          .when(() -> Workflow.newActivityStub(SampleActivity.class, defaultOptions()))
          .thenReturn(expectedStub);

      SampleActivity result =
          activityManager.newActivityStub("SAMPLE_TX", SampleActivity.class, defaultOptions());
      assertThat(result).isSameAs(expectedStub);
    }
  }

  @Test
  @DisplayName("shouldDelegateGetActivityCodesToRegistry")
  void shouldDelegateGetActivityCodesToRegistry() {
    when(artifactRegistry.getActivityCodes()).thenReturn(Set.of("A", "B"));
    assertThat(activityManager.getActivityCodes()).containsExactly("A", "B");
  }

  @Test
  @DisplayName("shouldDelegateGetActivityToRegistry")
  void shouldDelegateGetActivityToRegistry() {
    SampleActivity impl = mock(SampleActivity.class);
    when(artifactRegistry.getActivity("SAMPLE_TX", SampleActivity.class)).thenReturn(impl);
    assertThat(activityManager.getActivity("SAMPLE_TX", SampleActivity.class)).isSameAs(impl);
  }

  @Test
  @DisplayName("shouldDelegateGetActivityInterfaceToRegistry")
  void shouldDelegateGetActivityInterfaceToRegistry() {
    assertThat(activityManager.getActivityInterface("SAMPLE_TX")).isEqualTo(SampleActivity.class);
  }

  @Test
  @DisplayName("shouldRegisterAllActivitiesWithWorker")
  void shouldRegisterAllActivitiesWithWorker() {
    Object impl1 = new Object();
    Object impl2 = new Object();

    when(artifactRegistry.getActivityCodes()).thenReturn(Set.of("ACT_1", "ACT_2"));
    when(artifactRegistry.getActivityInterface("ACT_1")).thenReturn(SampleActivity.class);
    when(artifactRegistry.getActivityInterface("ACT_2")).thenReturn(SampleActivity.class);
    when(artifactRegistry.getActivity("ACT_1", SampleActivity.class)).thenReturn(impl1);
    when(artifactRegistry.getActivity("ACT_2", SampleActivity.class)).thenReturn(impl2);

    Worker worker = mock(Worker.class);
    activityManager.registerActivities(worker);

    verify(worker).registerActivitiesImplementations(impl1, impl2);
  }

  @Test
  @DisplayName("shouldNotCallWorkerWhenNoActivitiesRegistered")
  void shouldNotCallWorkerWhenNoActivitiesRegistered() {
    when(artifactRegistry.getActivityCodes()).thenReturn(Set.of());

    Worker worker = mock(Worker.class);
    activityManager.registerActivities(worker);

    verify(worker).registerActivitiesImplementations();
  }

  private static ActivityOptions defaultOptions() {
    return ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(30))
        .build();
  }
}

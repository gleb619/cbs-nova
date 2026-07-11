package cbs.nova.temporal.example.complex;

import static org.assertj.core.api.Assertions.assertThat;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Timeout(value = 5, unit = TimeUnit.SECONDS)
class BookingWorkflowTest {
  private static final String TASK_QUEUE = "booking-task-queue";

  private TestWorkflowEnvironment testEnv;
  private TestBookingActivities activities;

  @BeforeEach
  void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    Worker worker = testEnv.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(BookingWorkflowImpl.class);
    activities = new TestBookingActivities();
    worker.registerActivitiesImplementations(activities);
    testEnv.start();
  }

  @AfterEach
  void tearDown() {
    testEnv.shutdown();
  }

  @Test
  void confirmsBooking() {
    BookingWorkflow workflow = newStub("confirm-test");

    assertThat(workflow.book("alice")).isEqualTo("Booking confirmed for alice");
    assertThat(activities.actions).containsExactly("bookFlight", "bookHotel", "bookCar");
  }

  @Test
  void compensatesWhenActivityFails() {
    activities.failHotel = true;
    BookingWorkflow workflow = newStub("failure-test");

    assertThat(workflow.book("alice")).isEqualTo("Booking cancelled for alice");
    assertThat(activities.actions).containsExactly("bookFlight", "cancelFlight");
  }

  @Test
  void cancelsAfterConfirmationAndCompensates() {
    BookingWorkflow workflow = newStub("signal-test");
    WorkflowClient.start(workflow::book, "bob");

    await(() -> "CONFIRMED".equals(workflow.getStatus()));
    workflow.cancelBooking();

    WorkflowStub stub = WorkflowStub.fromTyped(workflow);
    String result = stub.getResult(String.class);

    assertThat(result).isEqualTo("Booking cancelled for bob");
    assertThat(activities.actions)
            .containsExactly(
                    "bookFlight", "bookHotel", "bookCar", "cancelCar", "cancelHotel",
                    "cancelFlight");
  }

  private BookingWorkflow newStub(String workflowId) {
    return testEnv
            .getWorkflowClient()
            .newWorkflowStub(
                    BookingWorkflow.class,
                    WorkflowOptions.newBuilder()
                            .setTaskQueue(TASK_QUEUE)
                            .setWorkflowId(workflowId)
                            .build());
  }

  private static void await(Supplier<Boolean> condition) {
    for (int i = 0; i < 100; i++) {
      if (condition.get()) {
        return;
      }
      try {
        Thread.sleep(Duration.ofMillis(20).toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for condition");
      }
    }
    throw new AssertionError("Condition was not met in time");
  }
}

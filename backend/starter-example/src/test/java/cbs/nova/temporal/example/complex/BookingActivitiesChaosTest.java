package cbs.nova.temporal.example.complex;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Chaos tests for the booking activities using a local WireMock server. The external reservation
 * services are stubbed to fail intermittently so that we can verify Temporal's retry behaviour and
 * the saga compensation path.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class BookingActivitiesChaosTest {
  private static final String TASK_QUEUE = "booking-chaos-task-queue";

  private TestWorkflowEnvironment testEnv;
  private WireMockServer wireMockServer;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    wireMockServer.start();
    WireMock.configureFor(wireMockServer.port());

    testEnv = TestWorkflowEnvironment.newInstance();
    Worker worker = testEnv.newWorker(TASK_QUEUE);
    worker.registerWorkflowImplementationTypes(BookingWorkflowImpl.class);
    worker.registerActivitiesImplementations(
            new BookingActivitiesImpl("http://localhost:" + wireMockServer.port()));
    testEnv.start();
  }

  @AfterEach
  void tearDown() {
    testEnv.shutdown();
    wireMockServer.stop();
  }

  @Test
  void workflowSucceedsDespiteIntermittent503Responses() {
    stubServiceWithTransientFailures("/flight", 1);
    stubServiceWithTransientFailures("/hotel", 2);
    stubServiceWithTransientFailures("/car", 3);

    BookingWorkflow workflow = newStub("chaos-success");

    assertThat(workflow.book("alice")).isEqualTo("Booking confirmed for alice");
    assertThat(workflow.getStatus()).isEqualTo("CONFIRMED");

    wireMockServer.verify(2, postRequestedFor(urlEqualTo("/flight")));
    wireMockServer.verify(3, postRequestedFor(urlEqualTo("/hotel")));
    wireMockServer.verify(4, postRequestedFor(urlEqualTo("/car")));
  }

  @Test
  void workflowCompensatesWhenServiceNeverRecovers() {
    wireMockServer.stubFor(
            post(urlEqualTo("/flight"))
                    .willReturn(aResponse().withStatus(200).withBody("flight booked")));
    wireMockServer.stubFor(
            post(urlEqualTo("/hotel"))
                    .willReturn(aResponse().withStatus(200).withBody("hotel booked")));
    wireMockServer.stubFor(
            post(urlEqualTo("/car"))
                    .willReturn(aResponse().withStatus(503).withBody("car unavailable")));
    wireMockServer.stubFor(
            post(urlEqualTo("/flight/cancel"))
                    .willReturn(aResponse().withStatus(200).withBody("flight cancelled")));
    wireMockServer.stubFor(
            post(urlEqualTo("/hotel/cancel"))
                    .willReturn(aResponse().withStatus(200).withBody("hotel cancelled")));

    BookingWorkflow workflow = newStub("chaos-compensate");

    assertThat(workflow.book("bob")).isEqualTo("Booking cancelled for bob");
    assertThat(workflow.getStatus()).isEqualTo("CANCELLED");

    wireMockServer.verify(1, postRequestedFor(urlEqualTo("/flight")));
    wireMockServer.verify(1, postRequestedFor(urlEqualTo("/hotel")));
    wireMockServer.verify(5, postRequestedFor(urlEqualTo("/car")));

    wireMockServer.verify(1, postRequestedFor(urlEqualTo("/flight/cancel")));
    wireMockServer.verify(1, postRequestedFor(urlEqualTo("/hotel/cancel")));
    wireMockServer.verify(0, postRequestedFor(urlEqualTo("/car/cancel")));
  }

  private void stubServiceWithTransientFailures(String path, int failuresBeforeSuccess) {
    String scenarioName = "scenario" + path.replace("/", "-");
    String state = Scenario.STARTED;
    for (int i = 0; i < failuresBeforeSuccess; i++) {
      String nextState = state + "-" + (i + 1);
      wireMockServer.stubFor(
              post(urlEqualTo(path))
                      .inScenario(scenarioName)
                      .whenScenarioStateIs(state)
                      .willSetStateTo(nextState)
                      .willReturn(aResponse().withStatus(503).withBody("unavailable")));
      state = nextState;
    }
    wireMockServer.stubFor(
            post(urlEqualTo(path))
                    .inScenario(scenarioName)
                    .whenScenarioStateIs(state)
                    .willReturn(aResponse().withStatus(200).withBody("ok")));
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
}

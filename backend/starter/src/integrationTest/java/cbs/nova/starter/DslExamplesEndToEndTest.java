package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslTemporalProcessRequest;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dslexamples.BatchModels.BatchIn;
import cbs.nova.dslexamples.BatchModels.BatchItem;
import cbs.nova.dslexamples.BatchModels.BatchOut;
import cbs.nova.dslexamples.batchprocessing.v1.BatchProcessingProcessWorkflow;
import io.restassured.RestAssured;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = IntegrationTestApplication.class, properties = {"dsl.task-queue=BatchProcessing-queue"})
@Import(TemporalTestConfiguration.class)
class DslExamplesEndToEndTest extends BaseContainers {

  @LocalServerPort
  private int port;

  @Autowired
  private WorkflowClient workflowClient;

  private static KeycloakRealmInitializer keycloakRealm;

  @BeforeAll
  static void initKeycloak() {
    GlobalManager.getInstance().resetForTests();
    keycloakRealm = new KeycloakRealmInitializer(KEYCLOAK);
    keycloakRealm.initialize();
  }

  @BeforeEach
  void setUp() {
    RestAssured.port = port;
    RestAssured.basePath = "";
  }

  @AfterEach
  void tearDown() {
    GlobalManager.getInstance().resetForTests();
  }

  @Test
  void generatedDslWorkflowExecutesThroughTemporal() throws TimeoutException {
    String workflowId = "batch-processing-test-" + System.currentTimeMillis();
    var input = new BatchIn(List.of(new BatchItem("a", 1), new BatchItem("b", 2)));
    var request = new DslTemporalProcessRequest(workflowId, input);

    var stub = workflowClient.newWorkflowStub(
            BatchProcessingProcessWorkflow.class,
            WorkflowOptions.newBuilder()
                    .setTaskQueue("BatchProcessing-queue")
                    .setWorkflowId(workflowId)
                    .build());

    WorkflowStub.fromTyped(stub).start(request);
    Object result = WorkflowStub.fromTyped(stub).getResult(30, TimeUnit.SECONDS, Object.class);

    BatchOut out = (BatchOut) result;
    assertThat(out.total()).isEqualTo(3);
    assertThat(out.summary()).isEqualTo("Processed: a=1, b=2");
  }

  @Test
  @Disabled("blocked by missing T51 Keycloak resource-server wiring; re-enable when /api/dsl/** is JWT-secured")
  void securedRestEndpointRunsDslWithKeycloakToken() {
    String accessToken = fetchAccessToken();

    RestAssured.given()
            .auth()
            .oauth2(accessToken)
            .contentType("application/json")
            .body("""
                    {"body":"integration-test"}""")
            .when()
            .post("/api/dsl/run/SampleProcess")
            .then()
            .statusCode(200);
  }

  private String fetchAccessToken() {
    return RestAssured.given()
            .contentType("application/x-www-form-urlencoded")
            .formParam("grant_type", "password")
            .formParam("client_id", keycloakRealm.getClientId())
            .formParam("client_secret", keycloakRealm.getClientSecret())
            .formParam("username", keycloakRealm.getUsername())
            .formParam("password", keycloakRealm.getPassword())
            .when()
            .post(keycloakRealm.tokenEndpoint())
            .then()
            .statusCode(200)
            .extract()
            .path("access_token");
  }
}

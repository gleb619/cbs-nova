package cbs.nova.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.generated.sampleprocess.v1.SampleProcessProcessWorkflow;
import io.restassured.RestAssured;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = IntegrationTestApplication.class)
@Import(TemporalTestConfiguration.class)
class DslExamplesEndToEndTest extends BaseContainers {

  @LocalServerPort
  private int port;

  @Autowired
  private WorkflowClient workflowClient;

  private static KeycloakRealmInitializer keycloakRealm;

  @BeforeAll
  static void initKeycloak() {
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
    var stub = workflowClient.newWorkflowStub(
            SampleProcessProcessWorkflow.class,
            WorkflowOptions.newBuilder()
                    .setTaskQueue("SampleProcess-queue")
                    .setWorkflowId("sample-process-test-" + System.currentTimeMillis())
                    .build());

    WorkflowStub.fromTyped(stub).start("integration-test");
    Object result = WorkflowStub.fromTyped(stub).getResult(30, TimeUnit.SECONDS, Object.class);

    assertThat(result).isEqualTo("Hello from DSL: integration-test");
  }

  @Test
  @org.junit.jupiter.api.Disabled("blocked by missing T51 Keycloak resource-server wiring; re-enable when /api/dsl/** is JWT-secured")
  void securedRestEndpointRunsDslWithKeycloakToken() {
    String accessToken = fetchAccessToken();

    given()
            .auth()
            .oauth2(accessToken)
            .contentType("application/json")
            .body("{\"body\":\"integration-test\"}")
            .when()
            .post("/api/dsl/run/SampleProcess")
            .then()
            .statusCode(200);
  }

  private String fetchAccessToken() {
    return given()
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

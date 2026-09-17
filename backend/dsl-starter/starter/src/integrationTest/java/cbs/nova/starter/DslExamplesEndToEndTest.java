package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dslexamples.v1.BatchModels.BatchIn;
import cbs.nova.dslexamples.v1.BatchModels.BatchItem;
import cbs.nova.dslexamples.v1.BatchModels.BatchOut;
import cbs.nova.util.ServiceUtil;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = IntegrationTestApplication.class, properties = {
    "csb.dsl.worker.enabled=true",
    "cbs.dsl.task-queue=BatchProcessing-queue"})
@Import(TemporalTestConfiguration.class)
class DslExamplesEndToEndTest extends BaseContainers {

  @LocalServerPort
  private int port;

  private static KeycloakRealmInitializer keycloakRealm;

  @BeforeAll
  static void initKeycloak() {
    GlobalManager.globalManager().resetForTests();
    new DefinitionLoader().load(GlobalManager.globalManager());
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
    GlobalManager.globalManager().resetForTests();
    new DefinitionLoader().load(GlobalManager.globalManager());
  }

  @Test
  void generatedDslWorkflowExecutesThroughTemporal() {
    var input = new BatchIn(List.of(new BatchItem("a", 1), new BatchItem("b", 2)));

    var service = ServiceUtil.newService();
    Result<?> result = service.runProcess("BatchProcessing", input).result().join();

    assertThat(result.isSuccess()).as("result cause: %s", result.cause()).isTrue();
    BatchOut out = result.as(BatchOut.class);

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

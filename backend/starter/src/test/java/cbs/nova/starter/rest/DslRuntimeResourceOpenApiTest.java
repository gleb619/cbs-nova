package cbs.nova.starter.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import cbs.nova.starter.StarterApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(classes = StarterApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "dsl.worker.enabled=false")
class DslRuntimeResourceOpenApiTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired
  private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = webAppContextSetup(webApplicationContext).build();
  }

  @Test
  void previewAndExplainSchemasAreEnriched() throws Exception {
    MvcResult result = mockMvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode apiDocs = OBJECT_MAPPER.readTree(result.getResponse().getContentAsString());
    JsonNode schemas = apiDocs.path("components").path("schemas");

    JsonNode previewReport = assertSchemaExists(schemas, "PreviewReport");
    assertHasProperty(previewReport, "astTree");
    assertHasProperty(previewReport, "dryRunLogs");
    assertHasProperty(previewReport, "externalCalls");

    JsonNode explainReport = assertSchemaExists(schemas, "ExplainReport");
    assertHasProperty(explainReport, "astTree");
    assertHasProperty(explainReport, "dryRunLogs");
    assertHasProperty(explainReport, "mermaidDiagram");

    JsonNode callNode = assertSchemaExists(schemas, "CallNode");
    assertHasProperty(callNode, "name");
    assertHasProperty(callNode, "kind");
    assertHasProperty(callNode, "children");
    assertHasProperty(callNode, "externalCalls");

    assertResponseRefersTo(apiDocs, "/api/dsl/preview/{name}", "PreviewReport");
    assertResponseRefersTo(apiDocs, "/api/dsl/explain/{name}", "ExplainReport");
  }

  private static JsonNode assertSchemaExists(JsonNode schemas, String name) {
    assertThat(schemas.has(name))
            .as("OpenAPI schema '%s' should be defined", name)
            .isTrue();
    return schemas.path(name);
  }

  private static void assertHasProperty(JsonNode schema, String property) {
    assertThat(schema.path("properties").has(property))
            .as("Schema should define property '%s'", property)
            .isTrue();
  }

  private static void assertResponseRefersTo(JsonNode apiDocs, String path, String schemaName) {
    String ref = apiDocs.path("paths")
            .path(path)
            .path("post")
            .path("responses")
            .path("200")
            .path("content")
            .path("application/json")
            .path("schema")
            .path("$ref")
            .asText();
    assertThat(ref)
            .as("Path '%s' 200 response should reference %s", path, schemaName)
            .endsWith("#/components/schemas/" + schemaName);
  }
}

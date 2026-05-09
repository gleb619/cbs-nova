package cbs.dsl.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ParameterScannerTest {

  public record StringInput(String name) {}

  public record MultiInput(String name, int age, boolean active, double score) {}

  public static class FieldInput {
    public String title;
    public int count;
  }

  @Test
  @DisplayName("shouldScanRecordWithStringField")
  void shouldScanRecordWithStringField() {
    ParameterScanner.ParameterScanResult result = ParameterScanner.scan(StringInput.class);
    assertEquals(1, result.definitions().size());
    assertEquals("name", result.definitions().get(0).getName());
    assertEquals(
        ParameterDefinition.ParameterType.STRING, result.definitions().get(0).getType());
  }

  @Test
  @DisplayName("shouldScanRecordWithMultipleFieldsOfDifferentTypes")
  void shouldScanRecordWithMultipleFieldsOfDifferentTypes() {
    ParameterScanner.ParameterScanResult result = ParameterScanner.scan(MultiInput.class);
    assertEquals(4, result.definitions().size());
    assertEquals("name", result.definitions().get(0).getName());
    assertEquals(
        ParameterDefinition.ParameterType.STRING, result.definitions().get(0).getType());
    assertEquals("age", result.definitions().get(1).getName());
    assertEquals(
        ParameterDefinition.ParameterType.INTEGER, result.definitions().get(1).getType());
    assertEquals("active", result.definitions().get(2).getName());
    assertEquals(
        ParameterDefinition.ParameterType.BOOLEAN, result.definitions().get(2).getType());
    assertEquals("score", result.definitions().get(3).getName());
    assertEquals(
        ParameterDefinition.ParameterType.DECIMAL, result.definitions().get(3).getType());
  }

  @Test
  @DisplayName("shouldReturnIdenticalDefinitionsForSameClass")
  void shouldReturnIdenticalDefinitionsForSameClass() {
    ParameterScanner.ParameterScanResult r1 = ParameterScanner.scan(StringInput.class);
    ParameterScanner.ParameterScanResult r2 = ParameterScanner.scan(StringInput.class);
    assertEquals(r1.definitions().size(), r2.definitions().size());
    for (int i = 0; i < r1.definitions().size(); i++) {
      assertEquals(r1.definitions().get(i).getName(), r2.definitions().get(i).getName());
      assertEquals(r1.definitions().get(i).getType(), r2.definitions().get(i).getType());
    }
  }
}

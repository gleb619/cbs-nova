import static org.junit.jupiter.api.Assertions.*;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.context.HelperContext;
import cbs.dsl.builder.Dsl;
import cbs.dsl.builder.HelperDslObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class BankingHelpersDslTest {

  @Test
  @DisplayName("shouldBuildFindCustomerCodeHelperWhenGivenValidId")
  void shouldBuildFindCustomerCodeHelperWhenGivenValidId() {
    List<DslObject> helpers = Dsl.helpers()
        .helper("FIND_CUSTOMER_CODE", h -> h.parameters(reg -> reg.string("id"))
            .execute(input -> HelperContext.builder().params(input.params()).build()))
        .build();

    HelperDslObject helper = (HelperDslObject) helpers.getFirst();
    assertEquals("FIND_CUSTOMER_CODE", helper.code());

    HelperInput payload = HelperInput.builder().params(Map.of("id", "123")).build();
    HelperContext ctx = HelperContext.builder().params(Map.of("id", "123")).build();

    HelperContext result = helper.executeBlock().apply(ctx);
    assertEquals("CUST-123", result.params().get("customerCode"));
  }

  @Test
  @DisplayName("shouldBuildLoanConditionsByIdHelperWhenGivenValidLoanId")
  void shouldBuildLoanConditionsByIdHelperWhenGivenValidLoanId() {
    List<DslObject> helpers = Dsl.helpers()
        .helper("LOAN_CONDITIONS_BY_ID", h -> h.parameters(reg -> reg.number("loanId"))
            .execute(input -> HelperContext.builder().params(input.params()).build()))
        .build();

    HelperDslObject helper = (HelperDslObject) helpers.getFirst();
    assertEquals("LOAN_CONDITIONS_BY_ID", helper.code());

    HelperInput payload = HelperInput.builder().params(Map.of("loanId", 42L)).build();
    HelperContext ctx = HelperContext.builder().params(Map.of("loanId", 42L)).build();

    HelperContext result = helper.executeBlock().apply(ctx);
    assertEquals(42L, result.params().get("loanId"));
    assertEquals("USD", result.params().get("currency"));
    assertEquals("5.5", result.params().get("interestRate"));
  }

  @Test
  @DisplayName("shouldBuildSendFaultNotificationHelperWhenGivenValidInput")
  void shouldBuildSendFaultNotificationHelperWhenGivenValidInput() {
    List<DslObject> helpers = Dsl.helpers()
        .helper("SEND_FAULT_NOTIFICATION", h -> h.parameters(
                reg -> reg.string("customerId").string("error"))
            .execute(input -> HelperContext.builder().params(input.params()).build()))
        .build();

    HelperDslObject helper = (HelperDslObject) helpers.getFirst();
    assertEquals("SEND_FAULT_NOTIFICATION", helper.code());

    HelperInput payload =
        HelperInput.builder().params(Map.of("customerId", "C1", "error", "ERR")).build();
    HelperContext ctx = HelperContext.builder()
        .params(Map.of("customerId", "C1", "error", "ERR"))
        .build();

    HelperContext result = helper.executeBlock().apply(ctx);
    assertEquals(true, result.params().get("sent"));
    assertEquals("EMAIL", result.params().get("channel"));
  }

  @Test
  @DisplayName("shouldIndicateImplicitClassWhenFileHasNoExplicitTypeDeclaration")
  void shouldIndicateImplicitClassWhenFileHasNoExplicitTypeDeclaration() throws Exception {
    Path path = Path.of("src/BankingHelpersDsl.java");
    assertTrue(Files.exists(path), "DSL file should exist");

    String content = Files.readString(path);
    boolean hasExplicitType = content.matches("(?s).*\\b(class|interface|enum|record)\\s+\\w+.*");
    assertFalse(
        hasExplicitType, "DSL file must be an implicit class without explicit type declarations");
  }
}

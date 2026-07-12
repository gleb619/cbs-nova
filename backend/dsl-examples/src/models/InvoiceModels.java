
import java.util.List;

public class InvoiceModels {

  public record InvoiceIn(List<InvoiceLine> lines) {
  }

  public record InvoiceLine(String description, double unitPrice, int quantity) {
  }

  public record InvoiceOut(double subtotal, double tax, double total, String formatted) {
  }
}

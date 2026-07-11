package cbs.nova.dslexamples;

public class OrderSagaModels {

  public record OrderSagaIn(String orderId, int quantity) {
  }

  public record OrderSagaOut(String orderId, String status, String message) {
  }

  public record PaymentResult(String orderId, double amount, boolean charged) {
  }

  public record InventoryReservation(String orderId, int quantity) {
  }
}

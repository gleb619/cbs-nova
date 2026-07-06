package cbs.nova.dslmodel;

public record OrderSagaOut(String orderId, String status, String message) {
}

package cbs.nova.dslmodel;

public record PaymentResult(String orderId, double amount, boolean charged) {
}

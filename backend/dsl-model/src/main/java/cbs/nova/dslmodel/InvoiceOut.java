package cbs.nova.dslmodel;

public record InvoiceOut(double subtotal, double tax, double total, String formatted) {

}

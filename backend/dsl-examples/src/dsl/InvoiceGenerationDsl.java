import cbs.nova.dslexamples.InvoiceModels.*;


List<DslObject> define() {
  return Dsl.process("InvoiceGeneration")
      .input(InvoiceIn.class)
      .output(InvoiceOut.class)
      .execute(ctx -> {
        InvoiceIn in = ctx.body();
        double subtotal = in.lines().stream()
            .mapToDouble(l -> l.unitPrice() * l.quantity())
            .sum();
        double tax = subtotal * 0.2;
        double total = subtotal + tax;
        String formatted = String.format("Invoice: subtotal=%.2f tax=%.2f total=%.2f",
            subtotal, tax, total);
        return Result.success(new InvoiceOut(subtotal, tax, total, formatted));
      })
      .buildList();
}

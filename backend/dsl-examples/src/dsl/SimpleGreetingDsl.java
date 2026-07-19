List<DslObject> define() {
  var greetFn = Dsl.function("greetFn")
      .input(Map.class)
      .output(String.class)
      .execute(ctx -> {
        var input = ctx.body();
        var name = String.valueOf(input.getOrDefault("name", "world"));
        return Result.success("Hello, " + name + "!");
      })
      .build();

  var process = Dsl.process("SimpleGreeting")
      .parameters(reg -> reg.string("name"))
      .execute(ctx -> {
        var input = ctx.body();
        var greeting = ctx.runHelper(
            "greetFn",
            Map.of("name", input.values().getOrDefault("name", "world")));
        if (!greeting.isSuccess()) {
          return Result.failure(greeting.cause());
        }
        return Result.success(greeting.as(String.class));
      })
      .build();

  return List.of(greetFn, process);
}

import cbs.nova.dsl.*;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
  var greetFn = Dsl.function("greetFn")
      .execute(ctx -> {
        @SuppressWarnings("unchecked")
        var input = (Map<String, Object>) ctx.body();
        var name = String.valueOf(input.getOrDefault("name", "world"));
        return Result.success("Hello, " + name + "!");
      })
      .build();

  var process = Dsl.process("SimpleGreeting")
      .input(Map.class)
      .output(String.class)
      .execute(ctx -> {
        @SuppressWarnings("unchecked")
        var input = (Map<String, Object>) ctx.body();
        var greeting = ctx.runHelper(
            "greetFn",
            Map.of("name", input.getOrDefault("name", "world")));
        if (!greeting.isSuccess()) {
          return Result.failure(greeting.cause());
        }
        return Result.success(greeting.as(String.class));
      })
      .build();

  return List.of(greetFn, process);
}

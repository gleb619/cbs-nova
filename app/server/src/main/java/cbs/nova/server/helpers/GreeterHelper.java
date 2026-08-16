package cbs.nova.server.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import org.springframework.stereotype.Component;

@Helper(name = "greeter")
@Component
public class GreeterHelper implements Executable<GreeterIn, GreeterOut> {

  @Override
  public Result<GreeterOut> execute(Context<GreeterIn> ctx) {
    var input = ctx.body();
    var target = input.name();
    if (target == null || target.isBlank()) {
      target = "world";
    }
    return Result.success(new GreeterOut("Hello, " + target + "!"));
  }
}

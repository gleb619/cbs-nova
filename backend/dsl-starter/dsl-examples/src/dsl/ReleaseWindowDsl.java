import cbs.nova.dslexamples.v1.ReleaseWindowModels.*;
import cbs.nova.starter.helper.model.DateMathIn;
import cbs.nova.starter.helper.model.DateMathOut;
import cbs.nova.starter.helper.model.SemverIn;
import cbs.nova.starter.helper.model.SemverOut;

List<DslObject> define() {
  return Dsl.process("ReleaseWindow")
      .input(ReleaseIn.class)
      .output(ReleaseOut.class)
      .execute(ctx -> {
        ReleaseIn in = ctx.body();

        var cmp = ctx.runHelper("semver",
            new SemverIn("compare", null, in.currentVersion(), in.minimumVersion(),
                null, null, null, null, null, null, null));
        if (!cmp.isSuccess()) {
          return Result.failure(cmp.cause());
        }
        int comparison = ((Number) cmp.as(SemverOut.class).result()).intValue();
        boolean versionOk = comparison >= 0;

        var shifted = ctx.runHelper("dateMath",
            new DateMathIn("add", in.releaseDate(), null, in.daysToAdd(), "days", null));
        if (!shifted.isSuccess()) {
          return Result.failure(shifted.cause());
        }
        String rolloutDate = shifted.as(DateMathOut.class).value();

        return Result.success(new ReleaseOut(versionOk, rolloutDate));
      })
      .buildList();
}

import cbs.nova.dslexamples.ReleasePolicyModels.*;
import cbs.nova.starter.helper.model.SemverIn;
import cbs.nova.starter.helper.model.SemverOut;

List<DslObject> define() {
  return Dsl.process("ReleasePolicy")
      .input(ReleasePolicyIn.class)
      .output(ReleasePolicyOut.class)
      .execute(ctx -> {
        ReleasePolicyIn in = ctx.body();

        // Gate step 1: parse the incoming version into its components.
        var parsed = ctx.runHelper("semver",
            new SemverIn("parse", in.incomingVersion(), null, null, null, null, null, null,
                null, null, null));
        if (!parsed.isSuccess()) {
          return Result.failure(parsed.cause());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> components =
            (Map<String, Object>) parsed.as(SemverOut.class).result();
        int major = ((Number) components.get("major")).intValue();
        int minor = ((Number) components.get("minor")).intValue();
        int patch = ((Number) components.get("patch")).intValue();

        // Gate step 2: reject versions outside the accepted range (e.g. "^1.2.0").
        var check = ctx.runHelper("semver",
            new SemverIn("satisfies", in.incomingVersion(), null, null, in.requiredRange(), null,
                null, null, null, null, null));
        if (!check.isSuccess()) {
          return Result.failure(check.cause());
        }
        boolean rangeSatisfied = (Boolean) check.as(SemverOut.class).result();
        if (!rangeSatisfied) {
          return Result.success(
              new ReleasePolicyOut(false, major, minor, patch, false, null, null));
        }

        // Gate passed: bump to the next version for the requested increment.
        var bumped = ctx.runHelper("semver",
            new SemverIn("bump", in.incomingVersion(), null, null, null, in.bumpType(), null,
                null, null, null, null));
        if (!bumped.isSuccess()) {
          return Result.failure(bumped.cause());
        }
        String bumpedVersion = (String) bumped.as(SemverOut.class).result();

        // Stamp the bumped version with build metadata to form the release candidate.
        var candidate = ctx.runHelper("semver",
            new SemverIn("format", null, null, null, null, null, major, minor, patch + 1, null,
                in.buildMetadata()));
        if (!candidate.isSuccess()) {
          return Result.failure(candidate.cause());
        }
        String nextCandidate = (String) candidate.as(SemverOut.class).result();

        return Result.success(
            new ReleasePolicyOut(true, major, minor, patch, true, bumpedVersion, nextCandidate));
      })
      .buildList();
}

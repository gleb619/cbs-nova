import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject;
import cbs.nova.dsl.Result;

import java.util.List;
import java.util.Map;

List<DslObject> define() {
  return Dsl.process("SignalProbe")
          .input(String.class)
          .output(String.class)
          .signal("approval", Map.class)
          .execute(ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> approval = ctx.awaitSignal("approval", Map.class);
            boolean approved = Boolean.TRUE.equals(approval.get("approved"));
            String reviewer = String.valueOf(approval.getOrDefault("reviewer", "unknown"));
            return Result.success("approved=" + approved + ":" + reviewer + ":" + ctx.body());
          })
          .buildList();
}

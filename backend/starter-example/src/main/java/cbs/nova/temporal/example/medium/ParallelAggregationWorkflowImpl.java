package cbs.nova.temporal.example.medium;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Medium-complexity workflow that invokes three activities in parallel and joins the results.
 */
public class ParallelAggregationWorkflowImpl implements ParallelAggregationWorkflow {

  private final AggregationActivities activities = Workflow.newActivityStub(
          AggregationActivities.class,
          ActivityOptions.newBuilder()
                  .setStartToCloseTimeout(Duration.ofSeconds(10))
                  .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                  .build());

  @Override
  public String aggregate(String input) {
    Promise<String> partA = Async.function(activities::fetchPartA, input);
    Promise<String> partB = Async.function(activities::fetchPartB, input);
    Promise<String> partC = Async.function(activities::fetchPartC, input);

    Promise.allOf(partA, partB, partC).get();
    return partA.get() + " " + partB.get() + " " + partC.get();
  }
}

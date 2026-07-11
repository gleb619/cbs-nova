package cbs.nova.temporal.example.simple;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/** Simple workflow implementation that delegates to a single activity. */
public class GreetingWorkflowImpl implements GreetingWorkflow {
  private final GreetingActivities activities = Workflow.newActivityStub(
          GreetingActivities.class,
          ActivityOptions.newBuilder()
                  .setStartToCloseTimeout(Duration.ofSeconds(5))
                  .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(1).build())
                  .build());

  @Override
  public String greet(String name) {
    return activities.composeGreeting(name);
  }
}

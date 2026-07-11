package cbs.nova.temporal.example.medium;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** Activities that fetch parts of a larger result independently. */
@ActivityInterface
public interface AggregationActivities {
  @ActivityMethod
  String fetchPartA(String input);

  @ActivityMethod
  String fetchPartB(String input);

  @ActivityMethod
  String fetchPartC(String input);
}

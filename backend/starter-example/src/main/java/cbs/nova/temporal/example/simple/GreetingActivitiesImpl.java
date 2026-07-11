package cbs.nova.temporal.example.simple;

/** Plain implementation of the greeting activity. */
public class GreetingActivitiesImpl implements GreetingActivities {
  @Override
  public String composeGreeting(String name) {
    return "Hello, " + name + "!";
  }
}

package cbs.nova.temporal.example.simple;

public class GreetingActivitiesImpl implements GreetingActivities {

  @Override
  public String composeGreeting(String name) {
    return "Hello, " + name + "!";
  }
}

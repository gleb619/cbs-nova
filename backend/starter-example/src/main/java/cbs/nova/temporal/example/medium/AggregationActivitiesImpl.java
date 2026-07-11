package cbs.nova.temporal.example.medium;

public class AggregationActivitiesImpl implements AggregationActivities {

  @Override
  public String fetchPartA(String input) {
    return "A-" + input;
  }

  @Override
  public String fetchPartB(String input) {
    return "B-" + input;
  }

  @Override
  public String fetchPartC(String input) {
    return "C-" + input;
  }
}

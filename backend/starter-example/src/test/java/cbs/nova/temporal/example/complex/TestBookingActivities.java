package cbs.nova.temporal.example.complex;

import java.util.ArrayList;
import java.util.List;

public class TestBookingActivities implements BookingActivities {

  final List<String> actions = new ArrayList<>();

  boolean failFlight = false;
  boolean failHotel = false;

  @Override
  public void bookFlight(String userId) {
    if (failFlight) {
      throw new RuntimeException("Flight unavailable");
    }
    actions.add("bookFlight");
  }

  @Override
  public void bookHotel(String userId) {
    if (failHotel) {
      throw new RuntimeException("Hotel unavailable");
    }
    actions.add("bookHotel");
  }

  @Override
  public void bookCar(String userId) {
    actions.add("bookCar");
  }

  @Override
  public void cancelFlight(String userId) {
    actions.add("cancelFlight");
  }

  @Override
  public void cancelHotel(String userId) {
    actions.add("cancelHotel");
  }

  @Override
  public void cancelCar(String userId) {
    actions.add("cancelCar");
  }
}

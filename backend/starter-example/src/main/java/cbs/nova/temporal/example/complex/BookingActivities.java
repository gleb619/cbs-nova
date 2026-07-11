package cbs.nova.temporal.example.complex;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface BookingActivities {
  @ActivityMethod
  void bookFlight(String userId);

  @ActivityMethod
  void bookHotel(String userId);

  @ActivityMethod
  void bookCar(String userId);

  @ActivityMethod
  void cancelFlight(String userId);

  @ActivityMethod
  void cancelHotel(String userId);

  @ActivityMethod
  void cancelCar(String userId);
}

package cbs.nova.temporal.example.complex;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** Activities used by the booking saga: book and compensate each reservation. */
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

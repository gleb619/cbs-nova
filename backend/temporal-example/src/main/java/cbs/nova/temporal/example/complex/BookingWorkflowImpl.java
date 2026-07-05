package cbs.nova.temporal.example.complex;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Complex workflow implementation that:
 *
 * <ul>
 * <li>books a flight, hotel and car sequentially;
 * <li>compensates already booked items when a later step fails or a cancel signal arrives;
 * <li>exposes status via a query method and accepts cancellation via a signal.
 * </ul>
 */
public class BookingWorkflowImpl implements BookingWorkflow {
  private final BookingActivities activities = Workflow.newActivityStub(
          BookingActivities.class,
          ActivityOptions.newBuilder()
                  .setStartToCloseTimeout(Duration.ofSeconds(5))
                  .setRetryOptions(
                          RetryOptions.newBuilder()
                                  .setInitialInterval(Duration.ofMillis(100))
                                  .setBackoffCoefficient(2.0)
                                  .setMaximumInterval(Duration.ofSeconds(1))
                                  .setMaximumAttempts(5)
                                  .build())
                  .build());

  private final List<String> booked = new ArrayList<>();
  private boolean cancelled = false;
  private String status = "PENDING";

  @Override
  public String book(String userId) {
    try {
      status = "BOOKING_FLIGHT";
      activities.bookFlight(userId);
      booked.add("FLIGHT");

      status = "BOOKING_HOTEL";
      activities.bookHotel(userId);
      booked.add("HOTEL");

      status = "BOOKING_CAR";
      activities.bookCar(userId);
      booked.add("CAR");

      status = "CONFIRMED";

      Workflow.await(Duration.ofSeconds(1), () -> cancelled);
      if (cancelled) {
        throw new BookingCancellationException();
      }

      return "Booking confirmed for " + userId;
    } catch (BookingCancellationException | ActivityFailure e) {
      compensate(userId);
      return "Booking cancelled for " + userId;
    }
  }

  private void compensate(String userId) {
    status = "COMPENSATING";
    List<String> toCompensate = new ArrayList<>(booked);
    Collections.reverse(toCompensate);
    for (String item : toCompensate) {
      switch (item) {
        case "FLIGHT" -> activities.cancelFlight(userId);
        case "HOTEL" -> activities.cancelHotel(userId);
        case "CAR" -> activities.cancelCar(userId);
      }
    }
    status = "CANCELLED";
  }

  @Override
  public void cancelBooking() {
    cancelled = true;
  }

  @Override
  public String getStatus() {
    return status;
  }
}

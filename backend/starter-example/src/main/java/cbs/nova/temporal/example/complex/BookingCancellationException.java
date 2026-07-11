package cbs.nova.temporal.example.complex;

/** Marker exception thrown when the booking workflow is explicitly cancelled. */
public class BookingCancellationException extends RuntimeException {
  private static final long serialVersionUID = 1L;
}

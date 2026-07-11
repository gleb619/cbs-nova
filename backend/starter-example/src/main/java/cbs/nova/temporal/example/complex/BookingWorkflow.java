package cbs.nova.temporal.example.complex;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface BookingWorkflow {
  @WorkflowMethod
  String book(String userId);

  @SignalMethod
  void cancelBooking();

  @QueryMethod
  String getStatus();
}

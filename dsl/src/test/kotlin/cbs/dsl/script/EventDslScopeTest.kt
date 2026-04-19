package cbs.dsl.script

import cbs.dsl.api.EventDefinition
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EventDslScopeTest {

  @Test
  @DisplayName("shouldRegisterEventWhenEventFunctionCalled")
  fun `should register event when event function called`() {
    val scope = TestEventDslScope()

    val event = scope.event("TEST_EVENT") {
      // Event definition block
    }

    assertNotNull(event)
    assertEquals("TEST_EVENT", event.code)
    assertEquals(1, scope.registeredEvents.size)
    assertEquals(event, scope.registeredEvents[0])
  }

  @Test
  @DisplayName("shouldRegisterMultipleEventsWhenCalledTwice")
  fun `should register multiple events when called twice`() {
    val scope = TestEventDslScope()

    val event1 = scope.event("EVENT_1") {
      // First event definition block
    }

    val event2 = scope.event("EVENT_2") {
      // Second event definition block
    }

    assertNotNull(event1)
    assertNotNull(event2)
    assertEquals("EVENT_1", event1.code)
    assertEquals("EVENT_2", event2.code)
    assertEquals(2, scope.registeredEvents.size)
    assertEquals(event1, scope.registeredEvents[0])
    assertEquals(event2, scope.registeredEvents[1])
  }

  @Test
  @DisplayName("shouldReturnEventDefinitionFromEventFunction")
  fun `should return event definition from event function`() {
    val scope = TestEventDslScope()

    val event = scope.event("TEST_EVENT") {
      context { ctx -> }
      display { ctx -> }
      transactions { }
      finish { ctx -> }
    }

    assertNotNull(event)
    assertEquals("TEST_EVENT", event.code)
    assertNotNull(event.contextBlock)
    assertNotNull(event.displayBlock)
    assertNotNull(event.transactionsBlock)
    assertNotNull(event.finishBlock)
  }

  // Test implementation of EventDslScope
  private class TestEventDslScope : EventDslScope() {
    // Expose registeredEvents for testing
    fun getRegisteredEvents(): List<EventDefinition> = registeredEvents.toList()
  }
}

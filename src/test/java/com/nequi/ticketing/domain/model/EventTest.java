package com.nequi.ticketing.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

  private static final EventId EVENT_ID = new EventId("event-1");
  private static final Instant EVENT_DATE = Instant.parse("2030-06-15T18:00:00Z");

  @Test
  void shouldCreateEventWithValidValues() {
    Event event = new Event(EVENT_ID, "Concert", EVENT_DATE, "Main Hall", 100);

    assertAll(
        () -> assertEquals(EVENT_ID, event.id()),
        () -> assertEquals("Concert", event.name()),
        () -> assertEquals(EVENT_DATE, event.date()),
        () -> assertEquals("Main Hall", event.venue()),
        () -> assertEquals(100, event.totalCapacity())
    );
  }

  @Test
  void shouldRejectNullName() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Event(EVENT_ID, null, EVENT_DATE, "Main Hall", 100));

    assertEquals("Event name is required", exception.getMessage());
  }

  @Test
  void shouldRejectBlankName() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Event(EVENT_ID, "   ", EVENT_DATE, "Main Hall", 100));

    assertEquals("Event name is required", exception.getMessage());
  }

  @Test
  void shouldRejectNullDate() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Event(EVENT_ID, "Concert", null, "Main Hall", 100));

    assertEquals("Event date is required", exception.getMessage());
  }

  @Test
  void shouldRejectNullVenue() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Event(EVENT_ID, "Concert", EVENT_DATE, null, 100));

    assertEquals("Venue is required", exception.getMessage());
  }

  @Test
  void shouldRejectBlankVenue() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Event(EVENT_ID, "Concert", EVENT_DATE, "  ", 100));

    assertEquals("Venue is required", exception.getMessage());
  }

  @Test
  void shouldRejectZeroCapacity() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Event(EVENT_ID, "Concert", EVENT_DATE, "Main Hall", 0));

    assertEquals("Capacity must be greater than zero", exception.getMessage());
  }

  @Test
  void shouldRejectNegativeCapacity() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        new Event(EVENT_ID, "Concert", EVENT_DATE, "Main Hall", -1));

    assertEquals("Capacity must be greater than zero", exception.getMessage());
  }

  @Test
  void shouldImplementRecordEqualityAndStringRepresentation() {
    Event event = new Event(EVENT_ID, "Concert", EVENT_DATE, "Main Hall", 100);
    Event sameEvent = new Event(EVENT_ID, "Concert", EVENT_DATE, "Main Hall", 100);
    Event differentEvent = new Event(EVENT_ID, "Concert", EVENT_DATE, "Main Hall", 200);

    assertEquals(event, sameEvent);
    assertEquals(event.hashCode(), sameEvent.hashCode());
    assertNotEquals(event, differentEvent);
    assertTrue(event.toString().contains("name=Concert"));
  }
}

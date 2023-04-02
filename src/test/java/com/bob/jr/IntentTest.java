package com.bob.jr;

import discord4j.core.event.domain.message.MessageCreateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class IntentTest {
    private String intentName;
    private String intentContext;
    private MessageCreateEvent messageCreateEvent;
    private Intent intent;

    @BeforeEach
    void setUp() {
        intentName = "sampleIntentName";
        intentContext = "sampleIntentContext";
        messageCreateEvent = mock(MessageCreateEvent.class);
        intent = new Intent(intentName, intentContext, messageCreateEvent);
    }

    @Test
    void testGetIntentName() {
        assertEquals(intentName, intent.getIntentName(), "Intent name should match the provided value.");
    }

    @Test
    void testGetIntentContext() {
        assertEquals(intentContext, intent.getIntentContext(), "Intent context should match the provided value.");
    }

    @Test
    void testGetMessageCreateEvent() {
        assertEquals(messageCreateEvent, intent.getMessageCreateEvent(), "MessageCreateEvent should match the provided value.");
    }
}

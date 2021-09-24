package com.bob.jr;

import discord4j.core.event.domain.message.MessageCreateEvent;

public class Intent {
    private final String intentName;
    private final String intentContext;
    private final MessageCreateEvent messageCreateEvent;

    public Intent(String intentName, String intentContext, MessageCreateEvent messageCreateEvent) {
        this.intentName = intentName;
        this.intentContext = intentContext;
        this.messageCreateEvent = messageCreateEvent;
    }

    public String getIntentName() {
        return intentName;
    }

    public String getIntentContext() {
        return intentContext;
    }

    public MessageCreateEvent getMessageCreateEvent() {
        return messageCreateEvent;
    }
}

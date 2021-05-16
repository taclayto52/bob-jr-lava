package com.bob.jr;

import com.google.protobuf.Message;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.discordjson.json.gateway.MessageCreate;

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

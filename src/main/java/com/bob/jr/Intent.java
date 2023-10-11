package com.bob.jr;

import discord4j.core.event.domain.message.MessageCreateEvent;

public record Intent(String intentName, String intentContext, MessageCreateEvent messageCreateEvent) {
}

package com.bob.jr.interfaces;

import com.bob.jr.Intent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import reactor.core.publisher.Mono;

public interface Command {
    Mono<Void> execute(Intent intent);
}

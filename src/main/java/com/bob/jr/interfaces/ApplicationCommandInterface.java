package com.bob.jr.interfaces;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import reactor.core.publisher.Mono;

public interface ApplicationCommandInterface {
    Mono<Void> execute(ApplicationCommandInteractionEvent intent);
}

package com.bob.jr.commands;

import reactor.core.publisher.Mono;

public interface CommandRegistrar {

    public Mono<Void> registerCommands();

}

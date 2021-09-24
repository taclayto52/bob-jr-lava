package com.bob.jr.interfaces;

import reactor.core.publisher.Mono;

public interface VoidCommand {
    Mono<Void> execute();
}

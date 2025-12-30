package com.bob.jr.commands;

import reactor.core.Disposable;

public interface CommandRegistrar {

    public Disposable registerCommands();

}

package com.bob.jr.commands;

import com.bob.jr.BobJr;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.discordjson.json.ApplicationCommandData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CommandStore {

    private static final Logger logger = LoggerFactory.getLogger(CommandStore.class);
    private final GatewayDiscordClient gatewayDiscordClient;
    private final Snowflake applicationId;

    private final HashSet<ApplicationCommandData> commandsRegistered = new HashSet<>();

    public CommandStore(GatewayDiscordClient gatewayDiscordClient) {
        this.gatewayDiscordClient = gatewayDiscordClient;
        this.applicationId = Objects.requireNonNull(gatewayDiscordClient.getApplicationInfo().block()).getId();

        checkExistingRegisteredCommands();
    }

    public void checkExistingRegisteredCommands() {
        final var applicationCommandList = gatewayDiscordClient
                .getRestClient().getApplicationService()
                .getGlobalApplicationCommands(applicationId.asLong())
                .collectList()
                .doOnError(BobJr::logThrowableAndPrintStackTrace)
                .block();

        commandsRegistered.addAll(applicationCommandList);
    }

    public Mono<Collection<ApplicationCommandData>> listApplicationCommands() {
        return Mono.just(commandsRegistered);
    }

    public Mono<Void> registerCommand(ApplicationCommandRequest applicationCommandRequest) {
        final var matchedRegisteredCommands = commandsRegistered.stream()
                .filter(applicationCommandData -> applicationCommandData.name().equals(applicationCommandRequest.name()))
                .count();
        if (matchedRegisteredCommands > 0) {
            logger.info(String.format("command with name: '%s' already registered", applicationCommandRequest.name()));
            return Mono.empty();
        }
        return gatewayDiscordClient
                .getRestClient().getApplicationService()
                .createGlobalApplicationCommand(applicationId.asLong(), applicationCommandRequest)
                .doOnSuccess(applicationCommandData -> {
                    logger.info(String.format("Sent request to create application command %s", applicationCommandData.name()));
                    commandsRegistered.add(applicationCommandData);
                })
                .doOnError(BobJr::logThrowableAndPrintStackTrace)
                .then();
    }

    public void deregisterCommand(List<String> commandNames) {
        final var filterPredicate = getPredicateForCommandName(commandNames);
        final var commandsToDeregister = commandsRegistered.stream()
                .filter(filterPredicate)
                .collect(Collectors.toList());

        Flux.fromIterable(commandsToDeregister).flatMap(applicationCommandData ->
                        gatewayDiscordClient.getRestClient().getApplicationService()
                                .deleteGlobalApplicationCommand(applicationId.asLong(), Long.parseLong(applicationCommandData.id()))
                                .doOnSuccess((voided) -> {
                                    logger.info(String.format("Deleted application command %s", applicationCommandData.name()));
                                    commandsRegistered.remove(applicationCommandData);
                                })
                                .doOnError(BobJr::logThrowableAndPrintStackTrace)
                                .then())
                .blockLast();
    }

    private Predicate<ApplicationCommandData> getPredicateForCommandName(final List<String> commandName) {
        return commandName.size() > 0 ?
                applicationCommandData -> commandName.contains(applicationCommandData.name()) :
                applicationCommandData -> true;
    }

}

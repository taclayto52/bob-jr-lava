package com.bob.jr.commands;

import com.bob.jr.Intent;
import com.bob.jr.interfaces.ApplicationCommandInterface;
import com.bob.jr.utils.ServerResources;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.discordjson.json.ApplicationCommandRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

public class BasicCommands implements CommandRegistrar {
    private static Logger logger = LoggerFactory.getLogger(BasicCommands.class);
    private final ServerResources serverResources;
    private final CommandStore commandStore;

    private final Map<String, ApplicationCommandInterface> basicCommandMap = new HashMap<>();
    private final String JOIN_COMMAND_HOOK = "join";
    private final String LEAVE_COMMAND_HOOK = "leave";
    private final String STOP_COMMAND_HOOK = "stop";

    public BasicCommands(ServerResources serverResources, CommandStore commandStore) {
        this.serverResources = serverResources;
        this.commandStore = commandStore;
    }

    public Disposable registerJoinCommand() {
        final ApplicationCommandRequest joinApplicationCommand = ApplicationCommandRequest.builder()
                .name("join")
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .description("Have the bot join the channel")
                .build();

        basicCommandMap.put(JOIN_COMMAND_HOOK, this::joinCommand);
        return commandStore.registerCommand(joinApplicationCommand);
    }


    public Mono<Void> pingCommand(Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .doOnSuccess(channel -> channel.createMessage("Pong!").block())
                .then();
    }

    public Disposable registerLeaveCommand() {
        final ApplicationCommandRequest leaveApplicationCommand = ApplicationCommandRequest.builder()
                .name("leave")
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .description("Have the bot join all channels")
                .build();


        basicCommandMap.put(LEAVE_COMMAND_HOOK, this::leaveCommand);
        return commandStore.registerCommand(leaveApplicationCommand);
    }

    public Disposable registerStopCommand() {
        final ApplicationCommandRequest leaveApplicationCommand = ApplicationCommandRequest.builder()
                .name("stop")
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .description(String.format("Stop whatever %s is doing", Objects.requireNonNull(serverResources.gatewayClient().getSelf().block()).getUsername()))
                .build();


        basicCommandMap.put(STOP_COMMAND_HOOK, this::stopCommand);
        return commandStore.registerCommand(leaveApplicationCommand);
    }

    public Mono<Void> joinCommandFunction(Member member) {
        return member.getVoiceState()
                .flatMap(VoiceState::getChannel)
                .flatMap(serverResources::joinVoiceChannel)
                .then();
    }

    // since the operations are functionally the same after we get the member, we should generalize that
    public Mono<Void> joinCommand(ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        return Mono.justOrEmpty(applicationCommandInteractionEvent.getInteraction().getMember().orElseThrow())
                .flatMap(this::joinCommandFunction)
                .then(applicationCommandInteractionEvent.reply("joining the channel!"))
                .then();
    }

    public Mono<Void> joinCommand(Intent intent) {
        return Mono.justOrEmpty(intent.messageCreateEvent().getMember().orElseThrow())
                .flatMap(this::joinCommandFunction)
                .then();
    }

    public Mono<Void> leaveCommandFunction(Snowflake guildSnowflake) {
        serverResources.trackScheduler().clearPlaylist();
        Optional<VoiceState> voiceStateOptional = Optional.ofNullable(serverResources.gatewayClient().getMemberById(guildSnowflake, serverResources.gatewayClient().getSelfId())
                .block()
                .getVoiceState()
                .block());
        return voiceStateOptional.orElseThrow().getChannel().block()
                .getVoiceConnection().block()
                .disconnect().then();
    }

    public Mono<Void> leaveCommand(ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        return Mono.justOrEmpty(applicationCommandInteractionEvent.getInteraction().getGuildId())
                .flatMap(this::leaveCommandFunction)
                .then(applicationCommandInteractionEvent.reply("Leaving the channel!"))
                .then();
    }

    public Mono<Void> leaveCommand(Intent intent) {
        return Mono.justOrEmpty(intent.messageCreateEvent().getGuildId())
                .flatMap(this::leaveCommandFunction)
                .then();
    }

    public void stopCommandFunction() {
        serverResources.trackScheduler().clearPlaylist();
    }

    public Mono<Void> stopCommand(ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        stopCommandFunction();
        return Mono.empty();
    }

    // just stop for god's sake
    public Mono<Void> stopCommand(Intent intent) {
        stopCommandFunction();
        return Mono.empty();
    }

    public Map<String, ApplicationCommandInterface> getApplicationCommandInterfaces() {
        return basicCommandMap;
    }

    @Override
    public Disposable registerCommands() {
        return Flux.fromIterable(List.of(registerJoinCommand(), registerLeaveCommand(), registerStopCommand()))
                .doOnComplete(() -> logger.info("Finished registering {}", BasicCommands.class.getName()))
                .blockLast();
    }
}

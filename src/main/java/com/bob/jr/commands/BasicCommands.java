package com.bob.jr.commands;

import com.bob.jr.Intent;
import com.bob.jr.interfaces.Command;
import com.bob.jr.utils.ServerResources;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import discord4j.common.util.Snowflake;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.voice.AudioProvider;
import reactor.core.publisher.Mono;

import java.util.Optional;

public class BasicCommands {

    private final ServerResources serverResources;

    public BasicCommands(ServerResources serverResources) {
        this.serverResources = serverResources;
    }

    public Mono<Void> joinCommand(Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(serverResources.getServerAudioProvider())))
                .then();
    }

    public Mono<Void> leaveCommand(Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent())
                .flatMap(event -> {
                    serverResources.getTrackScheduler().clearPlaylist();

                    Snowflake guildSnow = event.getGuild().block().getId();
                    Optional<VoiceState> voiceStateOptional = Optional.ofNullable(serverResources.getGatewayDiscordClient().getMemberById(guildSnow, serverResources.getGatewayDiscordClient().getSelfId())
                            .block()
                            .getVoiceState()
                            .block());
                    Optional<VoiceChannel> optionalVoiceChannel = voiceStateOptional.stream().findFirst().flatMap(voiceState -> Optional.ofNullable(voiceState.getChannel().block()));
                    optionalVoiceChannel.ifPresent(voiceChannel ->{
                        voiceChannel.getVoiceConnection().block().disconnect().block();
                    });
                    return Mono.empty();
                })
                .then();
    }

    // just stop for god's sake
    public Mono<Void> stop(Intent intent) {
        return Mono.justOrEmpty(serverResources.getAudioPlayer())
                .doOnNext(thePlayer -> {
                    serverResources.getTrackScheduler().clearPlaylist();
                })
                .then();
    }

}

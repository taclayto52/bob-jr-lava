package com.bob.jr;

import com.bob.jr.interfaces.Command;
import com.google.cloud.texttospeech.v1.Voice;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.channel.VoiceChannel;
import reactor.core.publisher.Mono;

import javax.swing.text.html.Option;
import java.util.Optional;

public class MakeTheBotLeave implements Command {
    private final GatewayDiscordClient client;
    private final AudioPlayer player;
    private final TrackScheduler scheduler;

    public MakeTheBotLeave (GatewayDiscordClient client, AudioPlayer player, TrackScheduler scheduler) {
        this.client = client;
        this.player = player;
        this.scheduler = scheduler;
    }

    @Override
    public Mono<Void> execute(Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent())
                .flatMap(event -> {
                    scheduler.clearPlaylist();

                    Snowflake guildSnow = event.getGuild().block().getId();
                    Optional<VoiceState> voiceStateOptional = Optional.ofNullable(client.getMemberById(guildSnow, client.getSelfId())
                            .block()
                            .getVoiceState()
                            .block());
                    Optional<VoiceChannel> optionalVoiceChannel = voiceStateOptional.stream().findFirst().flatMap(voiceState -> Optional.of(voiceState.getChannel().block()));
                    optionalVoiceChannel.ifPresent(voiceChannel ->{
                        voiceChannel.getVoiceConnection().block().disconnect().block();
                    });
                    return Mono.empty();
                })
                .then();
    }
}

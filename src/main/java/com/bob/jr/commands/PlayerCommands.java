package com.bob.jr.commands;

import com.bob.jr.Intent;
import com.bob.jr.utils.ServerResources;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static com.bob.jr.utils.LimitsHelper.MESSAGE_LIMIT;
import static com.bob.jr.utils.LimitsHelper.PLAYLIST_RETURN_LIMIT;

public class PlayerCommands {

    private final ServerResources serverResources;

    public PlayerCommands(ServerResources serverResources) {
        this.serverResources = serverResources;
    }

    public Mono<Void> play(Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(serverResources.getServerAudioProvider())))
                .doOnSuccess(voided -> serverResources.getAudioPlayerManager().loadItem(intent.getIntentContext(), serverResources.getTrackScheduler()))
                .then();
    }

    public Mono<Void> search(Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(serverResources.getServerAudioProvider())))
                .doOnSuccess(voided -> serverResources.getAudioPlayerManager().loadItem(String.format("ytsearch:%s", intent.getIntentContext()), serverResources.getTrackScheduler()))
                .then();
    }

    public Mono<Void> playlist(Intent intent) {
        final var scheduler = serverResources.getTrackScheduler();
        final var player = serverResources.getAudioPlayer();
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(messageChannel -> {
                    List<AudioTrack> audioTrackList = scheduler.getAudioTracks();
                    StringBuilder printString = new StringBuilder();
                    if(audioTrackList.isEmpty() && Optional.ofNullable(player.getPlayingTrack()).isEmpty()) {
                        printString.append(":no_mouth: No playlist currently set");
                    } else{
                        if(Optional.ofNullable(player.getPlayingTrack()).isPresent()) {
                            printString.append(String.format(":loud_sound: **Currently playing:** %s%n", player.getPlayingTrack().getInfo().title));
                        }
                        for(int i=0; i<audioTrackList.size(); i++) {
                            String appendString = String.format("%d: %s%n", i + 1, audioTrackList.get(i).getInfo().title);
                            if(printString.length() + appendString.length() + 20 >= MESSAGE_LIMIT
                                    || i == PLAYLIST_RETURN_LIMIT) {
                                printString.append(String.format("And %d more...", audioTrackList.size() - i));
                                break;
                            }
                            printString.append(appendString);
                        }
                    }

                    return messageChannel.createMessage(printString.toString());
                })
                .then();
    }

}

package com.bob.jr.commands;

import com.bob.jr.Intent;
import com.bob.jr.channelevents.ChannelWatcher;
import com.bob.jr.utils.ServerResources;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.bob.jr.utils.LimitsHelper.MESSAGE_LIMIT;
import static com.bob.jr.utils.LimitsHelper.PLAYLIST_RETURN_LIMIT;

public class PlayerCommands {

    private final ServerResources serverResources;
    private final Logger logger = LoggerFactory.getLogger(PlayerCommands.class.getName());

    public PlayerCommands(final ServerResources serverResources) {
        this.serverResources = serverResources;
    }

    public Mono<Void> setVolume(final Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .doOnSuccess(messageChannel -> {
                    var volume = -1; // -1 to indicate never set
                    final String intentContext = intent.getIntentContext();
                    if (intentContext != null) {
                        volume = Integer.parseInt(intentContext.split(" ")[0]);
                    }


                    if (volume != -1) {
                        serverResources.getAudioPlayer().setVolume(volume);
                    } else {
                        volume = serverResources.getAudioPlayer().getVolume();
                    }
                    messageChannel.createMessage(String.format("Volume set at %s", volume)).block();
                })
                .then();
    }

    public Mono<Void> play(final Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(serverResources.getServerAudioProvider())))
                .doOnSuccess(voided -> {
                    final var context = checkAndHandleFile(intent.getIntentContext());
                    serverResources.getAudioPlayerManager().loadItem(context, serverResources.getTrackScheduler());
                })
                .then();
    }

    public Mono<Void> search(final Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(serverResources.getServerAudioProvider())))
                .doOnSuccess(voided -> serverResources.getAudioPlayerManager().loadItem(String.format("ytsearch:%s", intent.getIntentContext()), serverResources.getTrackScheduler()))
                .then();
    }

    public Mono<Void> playAnnouncementTrack(final Intent intent) {
        final var splitIntent = intent.getIntentContext().trim().split(" ");
        var trackStartTime = -1;
        if (splitIntent.length == 2) {
            try {
                trackStartTime = Integer.parseInt(splitIntent[1]);
            } catch (final NumberFormatException nfe) {
                logger.error(String.format("bad track start time provided: %s", splitIntent[1]));
            }
        }
        final var context = checkAndHandleFile(splitIntent[0]);
        ChannelWatcher.playAnnouncementTrack(context, trackStartTime, serverResources);
        return Mono.empty();
    }

    public Mono<Void> playlist(final Intent intent) {
        final var scheduler = serverResources.getTrackScheduler();
        final var player = serverResources.getAudioPlayer();
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(messageChannel -> {
                    final List<AudioTrack> audioTrackList = scheduler.getAudioTracks();
                    final StringBuilder printString = new StringBuilder();
                    if (audioTrackList.isEmpty() && Optional.ofNullable(player.getPlayingTrack()).isEmpty()) {
                        printString.append(":no_mouth: No playlist currently set");
                    } else {
                        if (Optional.ofNullable(player.getPlayingTrack()).isPresent()) {
                            printString.append(String.format(":loud_sound: **Currently playing:** %s%n", player.getPlayingTrack().getInfo().title));
                        }
                        for (int i = 0; i < audioTrackList.size(); i++) {
                            final String appendString = String.format("%d: %s%n", i + 1, audioTrackList.get(i).getInfo().title);
                            if (printString.length() + appendString.length() + 20 >= MESSAGE_LIMIT
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


    private String checkAndHandleFile(final String intentContext) {
        if (intentContext.startsWith("file:")) {
            final var fileLocation = intentContext.split("file:");
            final var resource = getClass().getClassLoader().getResource("soundFiles/" + fileLocation[1]);
            return resource == null ? Objects.requireNonNull(getClass().getClassLoader().getResource("soundFiles/ERROR.opus")).getFile() : resource.getFile();
        } else {
            return intentContext;
        }
    }
}

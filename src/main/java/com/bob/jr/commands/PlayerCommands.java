package com.bob.jr.commands;

import com.bob.jr.Intent;
import com.bob.jr.interfaces.ApplicationCommandInterface;
import com.bob.jr.utils.ServerResources;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bob.jr.utils.ApplicationCommandUtil.getApplicationOptionBoolean;
import static com.bob.jr.utils.ApplicationCommandUtil.getApplicationOptionString;
import static com.bob.jr.utils.LimitsHelper.MESSAGE_LIMIT;
import static com.bob.jr.utils.LimitsHelper.PLAYLIST_RETURN_LIMIT;

public class PlayerCommands {

    private final ServerResources serverResources;
    private final CommandStore commandStore;
    private final Map<String, ApplicationCommandInterface> playerCommandMap = new HashMap<>();
    private final String PLAY_COMMAND_HOOK = "play";
    private final String SEARCH_COMMAND_HOOK = "search";
    private final String PLAYLIST_COMMAND_HOOK = "playlist";
    private final String VOLUME_COMMAND_HOOK = "volume";

    private final String PLAY_COMMAND_SOURCE_OPTION = "audio_source";
    private final String SEARCH_COMMAND_TERM_OPTION = "search_term";
    private final String JOIN_CHANNEL_OPTION = "join_channel";

    private final ApplicationCommandOptionData joinChannelOption = ApplicationCommandOptionData.builder()
            .name(JOIN_CHANNEL_OPTION)
            .description("Have the bot join your channel?")
            .type(ApplicationCommandOption.Type.BOOLEAN.getValue())
            .required(false)
            .build();

    public PlayerCommands(final ServerResources serverResources, final CommandStore commandStore) {
        this.serverResources = serverResources;
        this.commandStore = commandStore;
    }

    public Mono<Void> registerPlayCommand() {
        final var playCommandOption = ApplicationCommandOptionData.builder()
                .name(PLAY_COMMAND_SOURCE_OPTION)
                .description("URL to audio source")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .required(true)
                .build();

        final ApplicationCommandRequest playApplicationCommand = ApplicationCommandRequest.builder()
                .name(PLAY_COMMAND_HOOK)
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .description("Play an audio source")
                .addAllOptions(List.of(playCommandOption, joinChannelOption))
                .build();

        playerCommandMap.put(PLAY_COMMAND_HOOK, this::playCommand);
        return commandStore.registerCommand(playApplicationCommand);
    }

    public Mono<Void> registerSearchCommand() {
        final var searchCommandOption = ApplicationCommandOptionData.builder()
                .name(SEARCH_COMMAND_TERM_OPTION)
                .description("Term to search for")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .required(true)
                .build();
        final ApplicationCommandRequest searchApplicationCommand = ApplicationCommandRequest.builder()
                .name(SEARCH_COMMAND_HOOK)
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .description("Search all sources (YouTube, etc) for audio")
                .addAllOptions(List.of(searchCommandOption, joinChannelOption))
                .build();

        playerCommandMap.put(SEARCH_COMMAND_HOOK, this::searchCommand);
        return commandStore.registerCommand(searchApplicationCommand);
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

    public Mono<Void> playCommand(final Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(member -> playCommandFunction(member, intent.getIntentContext(), true))
                .then();
    }

    public Mono<Void> playCommand(final ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        final var playCommandSourceUrl = getApplicationOptionString(applicationCommandInteractionEvent, PLAY_COMMAND_SOURCE_OPTION);
        final var joinChannel = getApplicationOptionBoolean(applicationCommandInteractionEvent, JOIN_CHANNEL_OPTION);

        return Mono.justOrEmpty(applicationCommandInteractionEvent.getInteraction().getMember().orElseThrow())
                .flatMap(member -> playCommandFunction(member, playCommandSourceUrl, joinChannel))
                .then();
    }

    public Mono<Void> playCommandFunction(final Member member, final String resourceUrl, final boolean joinChannel) {
        final var prePlayMono = joinChannel ?
                member.getVoiceState().flatMap(VoiceState::getChannel).flatMap(VoiceChannel::join) :
                Mono.empty();
        return prePlayMono
                .doOnSuccess(voided -> serverResources.getAudioPlayerManager().loadItem(resourceUrl, serverResources.getTrackScheduler()))
                .then();
    }

    public Mono<Void> searchCommand(final Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(member -> searchCommandFunction(member, intent.getIntentContext(), true))
                .then();
    }

    public Mono<Void> searchCommand(final ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        final var searchCommandTerm = getApplicationOptionString(applicationCommandInteractionEvent, SEARCH_COMMAND_TERM_OPTION);

        return Mono.justOrEmpty(applicationCommandInteractionEvent.getInteraction().getMember().orElseThrow())
                .flatMap(member -> searchCommandFunction(member, searchCommandTerm, true))
                .then();
    }

    public Mono<Void> searchCommandFunction(final Member member, final String searchTerm, final boolean joinChannel) {
        final var preSearchMono = joinChannel ?
                member.getVoiceState().flatMap(VoiceState::getChannel).flatMap(VoiceChannel::join) :
                Mono.empty();

        return preSearchMono
                .doOnSuccess(voided -> serverResources.getAudioPlayerManager().loadItem(String.format("ytsearch:%s", searchTerm), serverResources.getTrackScheduler()))
                .then();
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

}

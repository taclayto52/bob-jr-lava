package com.bob.jr.commands;

import com.bob.jr.Intent;
import com.bob.jr.channelevents.ChannelWatcher;
import com.bob.jr.interfaces.ApplicationCommandInterface;
import com.bob.jr.utils.ServerResources;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bob.jr.utils.ApplicationCommandUtil.*;
import static com.bob.jr.utils.LimitsHelper.MESSAGE_LIMIT;
import static com.bob.jr.utils.LimitsHelper.PLAYLIST_RETURN_LIMIT;

public class PlayerCommands implements CommandRegistrar {

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
    private final String SET_VOLUME_OPTION = "set_volume";
    private final Logger logger = LoggerFactory.getLogger(PlayerCommands.class.getName());

    private final ApplicationCommandOptionData joinChannelOption = ApplicationCommandOptionData.builder()
            .name(JOIN_CHANNEL_OPTION)
            .description("Have the bot join your channel?")
            .type(ApplicationCommandOption.Type.BOOLEAN.getValue())
            .required(false)
            .build();

    private final ApplicationCommandOptionData setVolumeOption = ApplicationCommandOptionData.builder()
            .name(SET_VOLUME_OPTION)
            .description("Set the bot volume.")
            .type(ApplicationCommandOption.Type.INTEGER.getValue())
            .required(false)
            .build();

    public PlayerCommands(final ServerResources serverResources, final CommandStore commandStore) {
        this.serverResources = serverResources;
        this.commandStore = commandStore;
    }

    public Disposable registerPlayCommand() {
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

    public Disposable registerSearchCommand() {
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

    public Disposable registerPlaylistCommand() {
        final ApplicationCommandRequest playlistApplicationCommand = ApplicationCommandRequest.builder()
                .name(PLAYLIST_COMMAND_HOOK)
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .description("Get the current playlist.")
                .build();

        playerCommandMap.put(PLAYLIST_COMMAND_HOOK, this::playlistCommand);
        return commandStore.registerCommand(playlistApplicationCommand);
    }

    public Disposable registerVolumeCommand() {
        final ApplicationCommandRequest volumeApplicationCommand = ApplicationCommandRequest.builder()
                .name(VOLUME_COMMAND_HOOK)
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .description("Get (or set) the bot volume.")
                .addAllOptions(List.of(setVolumeOption))
                .build();

        playerCommandMap.put(VOLUME_COMMAND_HOOK, this::volumeCommand);
        return commandStore.registerCommand(volumeApplicationCommand);
    }

    public Mono<Void> volumeCommand(final Intent intent) {
        final var volumeString = intent.getIntentContext();
        final var channel = intent.getMessageCreateEvent().getMessage().getChannel().block();

        var volume = -1;
        if (volumeString != null) {
            volume = Integer.parseInt(volumeString.split(" ")[0]);
        }

        return volumeFunction(channel, volume);
    }

    public Mono<Void> volumeCommand(final ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        final var volume = getApplicationOptionLong(applicationCommandInteractionEvent, SET_VOLUME_OPTION);
        final var channel = applicationCommandInteractionEvent.getInteraction().getChannel().block();

        return volumeFunction(channel, (int) volume);
    }

    public Mono<Void> volumeFunction(MessageChannel messageChannel, int volume) {
        if (volume != -1) {
            serverResources.getAudioPlayer().setVolume(volume);
        } else {
            volume = serverResources.getAudioPlayer().getVolume();
        }
        return messageChannel.createMessage(String.format("Volume set at %s", volume)).then();
    }

    public Mono<Void> playCommand(final Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(member -> playCommandFunction(member, intent, true))
                .then();
    }

    public Mono<Void> playCommand(final ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        final var playCommandSourceUrl = getApplicationOptionString(applicationCommandInteractionEvent, PLAY_COMMAND_SOURCE_OPTION);
        final var joinChannel = getApplicationOptionBoolean(applicationCommandInteractionEvent, JOIN_CHANNEL_OPTION);

        return Mono.justOrEmpty(applicationCommandInteractionEvent.getInteraction().getMember().orElseThrow())
                .flatMap(member -> playCommandFunction(member, playCommandSourceUrl, joinChannel))
                .then();
    }

    public Mono<Void> playCommandFunction(final Member member, final Intent intent, final boolean joinChannel) {
        return playCommandFunction(member, intent.getIntentContext(), joinChannel);
    }

    public Mono<Void> playCommandFunction(final Member member, final String sourceUrl, final boolean joinChannel) {
        final var prePlayMono = joinChannel ?
                member.getVoiceState().flatMap(VoiceState::getChannel).flatMap(VoiceChannel::join) :
                Mono.empty();
        return prePlayMono
                .doOnSuccess(voided -> {
                    final var resourceLocation = checkAndHandleFile(sourceUrl);
                    serverResources.getAudioPlayerManager().loadItem(resourceLocation, serverResources.getTrackScheduler());
                })
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

    public Mono<Void> playlistCommand(final ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        final var messageChannel = applicationCommandInteractionEvent.getInteraction().getChannel().block();
        return Mono.just(playlistCommandFunction(messageChannel))
                .flatMap(applicationCommandInteractionEvent::reply)
                .then();
    }

    public Mono<Void> playlistCommand(final Intent intent) {
        final var messageChannel = intent.getMessageCreateEvent().getMessage().getChannel().block();
        return Mono.just(playlistCommandFunction(messageChannel))
                .flatMap(messageChannel::createMessage)
                .then();
    }

    public String playlistCommandFunction(final MessageChannel messageChannel) {
        final var scheduler = serverResources.getTrackScheduler();
        final var player = serverResources.getAudioPlayer();

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

        return printString.toString();
    }

    public Map<String, ApplicationCommandInterface> getApplicationCommandInterfaces() {
        return playerCommandMap;
    }


    private String checkAndHandleFile(final String intentContext) {
        var resourceLocation = intentContext;
        if (intentContext.startsWith("file:")) {
            final var fileLocation = intentContext.split("file:");
            resourceLocation = serverResources.handleFile(fileLocation[1]);
        }

        return resourceLocation;
    }

    @Override
    public Disposable registerCommands() {
        return Flux.fromIterable(List.of(registerPlaylistCommand(), registerPlayCommand(), registerSearchCommand(), registerVolumeCommand()))
                .doOnComplete(() -> logger.info("Finished registering {}", PlayerCommands.class.getName()))
                .blockLast();
    }
}

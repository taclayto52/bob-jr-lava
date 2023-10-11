package com.bob.jr;

import com.bob.jr.TextToSpeech.TextToSpeech;
import com.bob.jr.channelevents.ChannelWatcher;
import com.bob.jr.commands.*;
import com.bob.jr.health.HealthCheck;
import com.bob.jr.interfaces.ApplicationCommandInterface;
import com.bob.jr.interfaces.Command;
import com.bob.jr.interfaces.VoidCommand;
import com.bob.jr.utils.AudioTrackCache;
import com.bob.jr.utils.ServerResources;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.voice.AudioProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.bob.jr.utils.FluxUtils.logFluxError;

public class BobJr {

    public static final String PROJECT_ID = "937970633558"; // load these from environment var
    public static final String TOKEN_SECRET_ID = "discord-api-key";
    public static final String TOKEN_SECRET_VERSION = "1";
    private static final Map<String, Command> commands = new HashMap<>();
    private static final Map<String, ApplicationCommandInterface> applicationCommands = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(BobJr.class);
    private static final Set<VoidCommand> errorMessages = new HashSet<>();
    private static final ConcurrentHashMap<Guild, List<Role>> botRoles = new ConcurrentHashMap<>();
    private static String botName;
    private static String botNickName;

    static {
        commands.put("ping", intent -> intent.messageCreateEvent().getMessage()
                .getChannel()
                .flatMap(channel -> channel.createMessage("Pong!"))
                .then());
    }

    private final HeartBeats heartBeats;

    public BobJr(@Nullable final String token) {
        // setup health checks
        try {
            new Thread(new HealthCheck(8080)).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // setup GCloud text to speech
        final TextToSpeech tts = setupTextToSpeech();

        // try to get secret
        String secretToken = null;
        if (token == null) {
            try {
                final SecretManagerServiceClient secretClient = SecretManagerServiceClient.create();
                final AccessSecretVersionResponse response = secretClient.accessSecretVersion(SecretVersionName.newBuilder()
                        .setProject(PROJECT_ID)
                        .setSecret(TOKEN_SECRET_ID)
                        .setSecretVersion(TOKEN_SECRET_VERSION)
                        .build());
                secretToken = response.getPayload().getData().toStringUtf8();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }

        // setup client
        final String tokenProvided = secretToken == null ? token : secretToken;
        final GatewayDiscordClient client = DiscordClientBuilder.create(tokenProvided).build()
                .login()
                .block();
        botName = client.getSelf().block().getMention();
        final StringBuffer nickNameBuffer = new StringBuffer(botName);
        final int indexOfAt = nickNameBuffer.indexOf("@");
        botNickName = nickNameBuffer.replace(indexOfAt, indexOfAt + 1, "@!").toString();

        // get application id
        final var applicationId = client.getApplicationInfo().block().getId();


        // setup commands
        final ServerResources serverResources = setupPlayerAndCommands(tts, client);

        // setup Channel Watcher
        final ChannelWatcher channelWatcher = new ChannelWatcher(serverResources);

        // register events
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .flatMap(this::maybeGetGuildRoles)
                .flatMap(event -> Mono.just(event.getMessage().getContent())
                        .filter(content -> checkAndReturnBotName(content, event) != null)
                        .map(content -> extractIntent(content, event))
                        .flatMap(this::handleMessageCreateEvent))
                .subscribe();

        // application command handlers
        client.getEventDispatcher().on(ApplicationCommandInteractionEvent.class)
                .flatMap(applicationCommandInteractionEvent -> {
                    final var applicationCommand = getRegisteredCommandAction(applicationCommandInteractionEvent).orElseThrow();
                    return applicationCommand.execute(applicationCommandInteractionEvent);
                })
                .subscribe();

        client.getRestClient().getApplicationService().getGlobalApplicationCommands(applicationId.asLong())
                .flatMap(applicationCommandData -> {
                    logger.info(String.format("Got application command from global reg: %s", applicationCommandData.name()));
                    return Mono.empty();
                }).subscribe();

        // register member listener
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .flatMap(channelWatcher::voiceStateUpdateEventHandler)
                .subscribe();


        // register member listener
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .flatMap(channelWatcher::voiceStateUpdateEventHandler)
                .subscribe();

        // add heartbeats
        heartBeats = new HeartBeats();
        heartBeats.startAsync().awaitRunning();

        // block until disconnect
        client.onDisconnect().block();
        heartBeats.stopAsync();
    }

    public static void main(final String[] args) {
        final Optional<String> optionalSecret = args.length > 0 ?
                Optional.ofNullable(args[0]) :
                Optional.ofNullable(System.getenv("BOB_JR_DISCORD_AUTH_KEY"));

        new BobJr(optionalSecret.orElse(null));
    }

    public static TextToSpeech setupTextToSpeech() {
        TextToSpeech tts = null;
        try {
            tts = new TextToSpeech();
        } catch (final IOException ioe) {
            logger.error(ioe.getMessage());
        }
        return tts;
    }

    public static void logThrowableAndPrintStackTrace(final Throwable throwable) {
        logger.error(String.format("Error: %s", throwable.getMessage()));
        throwable.printStackTrace();
    }

    public Mono<MessageCreateEvent> maybeGetGuildRoles(final MessageCreateEvent messageCreateEvent) {
        final var guild = messageCreateEvent.getGuild().block();
        botRoles.computeIfAbsent(guild, guild1 -> guild1.getSelfMember().block().getRoles().collectList().block());
        return Mono.just(messageCreateEvent);
    }

    public Mono<Void> handleMessageCreateEvent(final Intent intent) {
        return Flux.fromIterable(commands.entrySet())
                .filter(entry -> intent.intentName().equals(entry.getKey()))
                .flatMap(entry -> entry.getValue().execute(intent))
                .doOnError(logFluxError(logger, "handleMessageCreateEvent"))
                .onErrorComplete()
                .next();
    }

    public Optional<ApplicationCommandInterface> getRegisteredCommandAction(final ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        return Optional.ofNullable(applicationCommands.get(applicationCommandInteractionEvent.getCommandName()));
    }

    public ServerResources setupPlayerAndCommands(final TextToSpeech tts, final GatewayDiscordClient client) {
        // setup audio player
        final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        final AudioPlayer player = playerManager.createPlayer();
        player.setVolume(50);
        final AudioPlayer announcementPlayer = playerManager.createPlayer();
        announcementPlayer.setPaused(true);

        // init AudioTrackCache
        final AudioTrackCache audioTrackCache = new AudioTrackCache();

        // create scheduler
        final TrackScheduler scheduler = new TrackScheduler(player, announcementPlayer, audioTrackCache);

        final AudioProvider provider = new LavaPlayerAudioProvider(player, announcementPlayer);
        final ServerResources serverResources = new ServerResources(provider, scheduler, client, player, playerManager, tts, audioTrackCache);
        final CommandStore commandStore = new CommandStore(client);
        final BasicCommands basicCommands = new BasicCommands(serverResources, commandStore);
        final PlayerCommands playerCommands = new PlayerCommands(serverResources, commandStore);
        final VoiceCommands voiceCommands = new VoiceCommands(serverResources, commandStore);

        registerApplicationCommands(List.of(basicCommands, playerCommands, voiceCommands));

        // register application commands
        applicationCommands.putAll(basicCommands.getApplicationCommandInterfaces());
        applicationCommands.putAll(playerCommands.getApplicationCommandInterfaces());

        // basic commands
        commands.put("join", basicCommands::joinCommand);
        commands.put("quit", basicCommands::leaveCommand);
        commands.put("leave", basicCommands::leaveCommand);
        commands.put("stop", basicCommands::stopCommand);

        // test commands
        commands.put("play-announcement-track", playerCommands::playAnnouncementTrack);

        // player commands
        commands.put("play", playerCommands::playCommand);
        commands.put("search", playerCommands::searchCommand);
        commands.put("playlist", playerCommands::playlistCommand);
        commands.put("volume", playerCommands::volumeCommand);

        // rick
        commands.put("rick", intent -> Mono.justOrEmpty(intent.messageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .doOnSuccess(connection -> playerManager.loadItem("https://www.youtube.com/watch?v=dQw4w9WgXcQ", scheduler))
                .then());

        // get voices
        commands.put("voices", voiceCommands::voicesCommand);
        commands.put("myvoice-all", voiceCommands::myVoiceAll);
        commands.put("myvoice", voiceCommands::myVoice);
        commands.put("pitch", voiceCommands::pitchCommand);
        commands.put("speaking-rate", voiceCommands::speakingRate);

        // tts
        commands.put("tts", voiceCommands::ttsCommand);

        return serverResources;
    }

    private Disposable registerApplicationCommands(final List<CommandRegistrar> commandRegistrars) {
        final var allCommandsRegistered = Flux.fromIterable(commandRegistrars).flatMap((commandRegistrar) -> Mono.just(commandRegistrar.registerCommands()))
                .blockLast();
        logger.info("All commands registered");
        return allCommandsRegistered;
    }

    public Intent extractIntent(final String incomingMessage, final MessageCreateEvent event) {
        String containedBotName = checkAndReturnBotName(incomingMessage, event);
        if (containedBotName == null) {
            return null;
        }

        final StringBuffer stringBuffer = new StringBuffer(incomingMessage);
        int botNameStartIndex = stringBuffer.indexOf(containedBotName);

        while (botNameStartIndex > -1) {
            final int botNameEndIndex = botNameStartIndex + containedBotName.length();
            stringBuffer.replace(botNameStartIndex, botNameEndIndex, "");

            containedBotName = checkAndReturnBotName(incomingMessage, event);
            botNameStartIndex = stringBuffer.indexOf(containedBotName);
        }

        final String trimmedMessage = stringBuffer.toString().trim();
        final int firstSpace = trimmedMessage.indexOf(" ");
        final String intentName;
        final String intentContext;
        if (firstSpace > -1) {
            intentName = trimmedMessage.substring(0, firstSpace).toLowerCase(Locale.ENGLISH);
            intentContext = trimmedMessage.substring(firstSpace + 1);
        } else {
            // no context provided, just intent name
            intentName = trimmedMessage;
            intentContext = null;
        }


        return new Intent(intentName, intentContext, event);
    }

    private String checkAndReturnBotName(final String message, final MessageCreateEvent event) {
        if (message.contains(botName)) {
            return botName;
        } else if (message.contains(botNickName)) {
            return botNickName;
        } else {
            final var roleSet = botRoles.get(event.getGuild().block());
            final var roleSetIterator = roleSet.iterator();
            while (roleSetIterator.hasNext()) {
                final var role = roleSetIterator.next();
                if (message.contains(role.getMention())) {
                    return role.getMention();
                }
            }

            return null;
        }
    }
}

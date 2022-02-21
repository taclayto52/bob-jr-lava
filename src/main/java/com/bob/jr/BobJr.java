package com.bob.jr;

import com.bob.jr.TextToSpeech.TextToSpeech;
import com.bob.jr.channelevents.ChannelWatcher;
import com.bob.jr.commands.BasicCommands;
import com.bob.jr.commands.PlayerCommands;
import com.bob.jr.commands.VoiceCommands;
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
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import discord4j.voice.AudioProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BobJr {

    private static final Map<String, Command> commands = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(BobJr.class);
    private static final Set<VoidCommand> errorMessages = new HashSet<>();
    private static final String PROJECT_ID = "937970633558"; // load these from environment var
    private static final String TOKEN_SECRET_ID = "discord-api-key";
    private static final String TOKEN_SECRET_VERSION = "1";
    private static String botName;
    private static String botNickName;
    private static ConcurrentHashMap<Guild, List<Role>> botRoles = new ConcurrentHashMap<>();

    static {
        commands.put("ping", intent -> intent.getMessageCreateEvent().getMessage()
                .getChannel()
                .flatMap(channel -> channel.createMessage("Pong!"))
                .then());
    }

    public BobJr(Optional<String> token) {
        // setup GCloud text to speech
        TextToSpeech tts = setupTextToSpeech();

        // try to get secret
        String secretToken = null;
        if (token.isEmpty()) {
            try {
                SecretManagerServiceClient secretClient = SecretManagerServiceClient.create();
                final AccessSecretVersionResponse response = secretClient.accessSecretVersion(SecretVersionName.newBuilder()
                        .setProject(PROJECT_ID)
                        .setSecret(TOKEN_SECRET_ID)
                        .setSecretVersion(TOKEN_SECRET_VERSION)
                        .build());
                secretToken = response.getPayload().getData().toStringUtf8();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // setup client
        final String tokenProvided = token.orElse(secretToken);
        final GatewayDiscordClient client = DiscordClientBuilder.create(tokenProvided).build()
                .login()
                .block();
        botName = client.getSelf().block().getMention();
        StringBuffer nickNameBuffer = new StringBuffer(botName);
        int indexOfAt = nickNameBuffer.indexOf("@");
        botNickName = nickNameBuffer.replace(indexOfAt, indexOfAt + 1, "@!").toString();

        // setup commands
        ServerResources serverResources = setupPlayerAndCommands(tts, client);

        // setup Channel Watcher
        ChannelWatcher channelWatcher = new ChannelWatcher(serverResources);

        // register events
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .flatMap(this::maybeGetGuildRoles)
                .flatMap(event -> Mono.just(event.getMessage().getContent())
                        .filter(content -> checkAndReturnBotName(content, event) != null)
                        .map(content -> extractIntent(content, event))
                        .flatMap(this::handleMessageCreateEvent))
                .subscribe();


        // register member listener
        client.getEventDispatcher().on(VoiceStateUpdateEvent.class)
                .flatMap(channelWatcher::voiceStateUpdateEventHandler)
                .subscribe();

        // block until disconnect
        client.onDisconnect().block();
    }

    public static void main(String[] args) {
        Optional<String> optionalSecret = args.length > 0 ? Optional.ofNullable(args[0]) : Optional.empty();
        new BobJr(optionalSecret);
    }

    public static TextToSpeech setupTextToSpeech() {
        TextToSpeech tts = null;
        try {
            tts = new TextToSpeech();
        } catch (IOException ioe) {
            logger.error(ioe.getMessage());
        }
        return tts;
    }

    public Mono<MessageCreateEvent> maybeGetGuildRoles(MessageCreateEvent messageCreateEvent) {
        final var guild = messageCreateEvent.getGuild().block();
        botRoles.computeIfAbsent(guild, guild1 -> guild1.getSelfMember().block().getRoles().collectList().block());
        return Mono.just(messageCreateEvent);
    }

    public Mono<Void> handleMessageCreateEvent(Intent intent) {
        return Flux.fromIterable(commands.entrySet())
                .filter(entry -> intent.getIntentName().equals(entry.getKey()))
                .flatMap(entry -> entry.getValue().execute(intent))
                .onErrorContinue((throwable, o) -> {
                    throwable.printStackTrace();
                })
                .next();
    }

    public ServerResources setupPlayerAndCommands(TextToSpeech tts, GatewayDiscordClient client) {
        // setup audio player
        final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        final AudioPlayer player = playerManager.createPlayer();
        final AudioPlayer announcementPlayer = playerManager.createPlayer();
        announcementPlayer.setPaused(true);

        // init AudioTrackCache
        final AudioTrackCache audioTrackCache = new AudioTrackCache();

        // create scheduler
        final TrackScheduler scheduler = new TrackScheduler(player, announcementPlayer, audioTrackCache);

        AudioProvider provider = new LavaPlayerAudioProvider(player, announcementPlayer);
        ServerResources serverResources = new ServerResources(provider, scheduler, client, player, playerManager, tts, audioTrackCache);
        BasicCommands basicCommands = new BasicCommands(serverResources);
        PlayerCommands playerCommands = new PlayerCommands(serverResources);
        VoiceCommands voiceCommands = new VoiceCommands(serverResources);

        // basic commands
        commands.put("join", basicCommands::joinCommand);
        commands.put("quit", basicCommands::leaveCommand);
        commands.put("leave", basicCommands::leaveCommand);
        commands.put("stop", basicCommands::stop);

        // player commands
        commands.put("play", playerCommands::play);
        commands.put("search", playerCommands::search);
        commands.put("playlist", playerCommands::playlist);

        // rick
        commands.put("rick", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .doOnSuccess(connection -> playerManager.loadItem("https://www.youtube.com/watch?v=dQw4w9WgXcQ", scheduler))
                .then());

        // get voices
        commands.put("voices", voiceCommands::voices);
        commands.put("myvoice-all", voiceCommands::myVoiceAll);
        commands.put("myvoice", voiceCommands::myVoice);
        commands.put("pitch", voiceCommands::pitch);
        commands.put("speaking-rate", voiceCommands::speakingRate);

        // tts
        commands.put("tts", voiceCommands::tts);

        return serverResources;
    }

    public Intent extractIntent(String incomingMessage, MessageCreateEvent event) {
        String containedBotName = checkAndReturnBotName(incomingMessage, event);
        if (containedBotName == null) {
            return null;
        }

        StringBuffer stringBuffer = new StringBuffer(incomingMessage);
        int botNameStartIndex = stringBuffer.indexOf(containedBotName);

        while (botNameStartIndex > -1) {
            int botNameEndIndex = botNameStartIndex + containedBotName.length();
            stringBuffer.replace(botNameStartIndex, botNameEndIndex, "");

            containedBotName = checkAndReturnBotName(incomingMessage, event);
            botNameStartIndex = stringBuffer.indexOf(containedBotName);
        }

        String trimmedMessage = stringBuffer.toString().trim();
        int firstSpace = trimmedMessage.indexOf(" ");
        String intentName;
        String intentContext;
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

    private String checkAndReturnBotName(String message, MessageCreateEvent event) {
        if (message.contains(botName)) {
            return botName;
        } else if (message.contains(botNickName)) {
            return botNickName;
        } else {
            final var roleSet = botRoles.get(event.getGuild().block());
            final var roleSetIterator = roleSet.iterator();
            while (roleSetIterator.hasNext()) {
                var role = roleSetIterator.next();
                if (message.contains(role.getMention())) {
                    return role.getMention();
                }
            }

            return null;
        }
    }
}

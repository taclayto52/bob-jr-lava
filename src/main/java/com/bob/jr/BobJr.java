package com.bob.jr;

import com.bob.jr.TextToSpeech.TextToSpeech;
import com.bob.jr.commands.BasicCommands;
import com.bob.jr.commands.PlayerCommands;
import com.bob.jr.commands.VoiceCommands;
import com.bob.jr.interfaces.Command;
import com.bob.jr.interfaces.VoidCommand;
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
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.voice.AudioProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;

public class BobJr {

    private static final Map<String, Command> commands = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(BobJr.class);

    private static String botName;
    private static String botNickName;

    private static final Set<VoidCommand> errorMessages = new HashSet<>();

    private static final String PROJECT_ID = "937970633558"; // load these from environment var
    private static final String TOKEN_SECRET_ID = "discord-api-key";
    private static final String TOKEN_SECRET_VERSION = "1";

    static {
        commands.put("ping", intent -> intent.getMessageCreateEvent().getMessage()
                .getChannel()
                .flatMap(channel -> channel.createMessage("Pong!"))
                .then());
    }

    public static void main(String[] args) {
        Optional<String> optionalSecret = args.length > 0 ? Optional.ofNullable(args[0]) : Optional.empty();
        new BobJr(optionalSecret);
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
        botNickName = nickNameBuffer.replace(indexOfAt, indexOfAt+1, "@!").toString();

        // setup commands
        setupPlayerAndCommands(tts, client);

        // register events
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .flatMap(event -> Mono.just(event.getMessage().getContent())
                        .filter(content -> checkAndReturnBotName(content) != null)
                        .map(content -> extractIntent(content, event))
                        .flatMap(this::handleMessageCreateEvent))
                .subscribe();

        // block until disconnect
        client.onDisconnect().block();
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

    public static TextToSpeech setupTextToSpeech() {
        TextToSpeech tts = null;
        try{
            tts = new TextToSpeech();
        } catch (IOException ioe) {
            logger.error(ioe.getMessage());
        }
        return tts;
    }

    public void setupPlayerAndCommands(TextToSpeech tts, GatewayDiscordClient client) {
        // setup audio player
        final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        final AudioPlayer player = playerManager.createPlayer();

        // create scheduler
        final TrackScheduler scheduler = new TrackScheduler(player);

        AudioProvider provider = new LavaPlayerAudioProvider(player);
        ServerResources serverResources = new ServerResources(provider, scheduler, client, player, playerManager, tts);
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
    }

    public Intent extractIntent(String incomingMessage, MessageCreateEvent event) {
        String containedBotName = checkAndReturnBotName(incomingMessage);
        if(containedBotName == null) {
            return null;
        }

        StringBuffer stringBuffer = new StringBuffer(incomingMessage);
        int botNameStartIndex = stringBuffer.indexOf(containedBotName);

        while(botNameStartIndex > -1) {
            int botNameEndIndex = botNameStartIndex + containedBotName.length();
            stringBuffer.replace(botNameStartIndex, botNameEndIndex, "");

            containedBotName = checkAndReturnBotName(incomingMessage);
            botNameStartIndex = stringBuffer.indexOf(containedBotName);
        }

        String trimmedMessage = stringBuffer.toString().trim();
        int firstSpace = trimmedMessage.indexOf(" ");
        String intentName;
        String intentContext;
        if(firstSpace > -1) {
            intentName = trimmedMessage.substring(0, firstSpace).toLowerCase(Locale.ENGLISH);
            intentContext = trimmedMessage.substring(firstSpace + 1);
        } else {
            // no context provided, just intent name
            intentName = trimmedMessage;
            intentContext = null;
        }


        return new Intent(intentName, intentContext, event);
    }

    private String checkAndReturnBotName(String message) {
        if(message.contains(botName)) {
            return botName;
        } else if (message.contains(botNickName)) {
            return botNickName;
        } else {
            return null;
        }
    }
}

package com.bob.jr;

import com.bob.jr.interfaces.Command;
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
import discord4j.discordjson.json.gateway.MessageCreate;
import discord4j.voice.AudioProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Text;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BobJr {

    private static final Map<String, Command> commands = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(BobJr.class);

    private static String botName;
    private static String botNickName;
    private static MakeTheBotLeave makeTheBotLeave;

    static {
        commands.put("ping", intent -> intent.getMessageCreateEvent().getMessage()
                .getChannel()
                .flatMap(channel -> channel.createMessage("Pong!"))
                .then());
    }

    public static void main(String[] args) {
        new BobJr(args[0]);
    }

    public BobJr(String token) {
        // setup GCloud text to speech
        TextToSpeech tts = setupTextToSpeech();

        // setup client
        final GatewayDiscordClient client = DiscordClientBuilder.create(token).build()
                .login()
                .block();
        makeTheBotLeave = new MakeTheBotLeave(client);
        botName = client.getSelf().block().getMention();
        StringBuffer nickNameBuffer = new StringBuffer(botName);
        int indexOfAt = nickNameBuffer.indexOf("@");
        botNickName = nickNameBuffer.replace(indexOfAt, indexOfAt+1, "@!").toString();

        // setup commands
        setupPlayerAndCommands(tts);

        // register events
        client.getEventDispatcher().on(MessageCreateEvent.class)
                .flatMap(event -> Mono.just(event.getMessage().getContent())
                        .filter(content -> checkAndReturnBotName(content) != null)
                        .map(content -> extractIntent(content, event))
                        .flatMap(intent -> Flux.fromIterable(commands.entrySet())
                                .filter(entry -> intent.getIntentName().equals(entry.getKey()))
                                .flatMap(entry -> entry.getValue().execute(intent))
                                .next()))
                .subscribe();

        // block until disconnect
        client.onDisconnect().block();
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

    public void setupPlayerAndCommands(TextToSpeech tts) {
        // setup audio player
        final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        final AudioPlayer player = playerManager.createPlayer();

        AudioProvider provider = new LavaPlayerAudioProvider(player);

        // register provider
        commands.put("join", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .then());

        // just stop for god sakes
        commands.put("stop", makeTheBotLeave);
        commands.put("quit", makeTheBotLeave);
        commands.put("leave", makeTheBotLeave);

        // create scheduler
        final TrackScheduler scheduler = new TrackScheduler(player);
        commands.put("play", intent -> Mono.justOrEmpty(intent.getIntentContext())
//                .map(content -> Arrays.asList(content.split(" ")))
                .doOnNext(command -> playerManager.loadItem(command, scheduler))
                .then());

        // rick
        commands.put("rick", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .doOnSuccess(connection -> playerManager.loadItem("https://www.youtube.com/watch?v=dQw4w9WgXcQ", scheduler))
                .then());

        // fart
//        commands.put("fart", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
//                .flatMap(Member::getVoiceState)
//                .flatMap(VoiceState::getChannel)
//                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
//                .doOnSuccess(connection -> {
//                    this.getClass().getClassLoader().getResource("fart").
//                    playerManager.loadItem("C:\\Users\\sausa\\OneDrive\\Documents\\GitHub\\bob-jr\\src\\main\\resources\\music.opus", scheduler);
//                })
//                .then());

        // sound
        commands.put("sound", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .doOnSuccess(connection -> playerManager.loadItem("C:\\Users\\sausa\\OneDrive\\Documents\\GitHub\\bob-jr\\src\\main\\resources\\music.opus", scheduler))
                .then());

        // tts
        commands.put("tts", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .flatMap(connection -> tts.synthesisTextMono(intent.getIntentContext()))
                .doOnSuccess(fileLocation -> playerManager.loadItem(fileLocation, scheduler))
                .then());
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
            intentContext = trimmedMessage.substring(firstSpace + 1).toLowerCase(Locale.ENGLISH);
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

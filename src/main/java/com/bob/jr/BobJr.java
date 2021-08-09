package com.bob.jr;

import com.bob.jr.interfaces.Command;
import com.bob.jr.utils.LimitsHelper;
import com.bob.jr.utils.RegexHelper;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.voice.AudioProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BobJr {

    private static final Map<String, Command> commands = new HashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(BobJr.class);

    private static Random random;
    private static File[] soundDirContents;
    private static String botName;
    private static String botNickName;
    private static MakeTheBotLeave makeTheBotLeave;

    private static final String PROJECT_ID = "937970633558"; // load these from environment var
    private static final String TOKEN_SECRET_ID = "discord-api-key";
    private static final String TOKEN_SECRET_VERSION = "1";

    static {
        commands.put("ping", intent -> intent.getMessageCreateEvent().getMessage()
                .getChannel()
                .flatMap(channel -> channel.createMessage("Pong!"))
                .then());

        random = new Random();
    }

    public static void main(String[] args) {
        Optional<String> optionalSecret = args.length > 0 ? Optional.ofNullable(args[0]) : Optional.empty();
        new BobJr(optionalSecret);
    }

    public BobJr(Optional<String> token) {
        // setup GCloud text to speech
        TextToSpeech tts = setupTextToSpeech();

        // setup sound directory
        soundDirContents = new File(this.getClass().getClassLoader().getResource("fart").getFile()).listFiles();

        // try to get secret
        String secretToken = null;
        if (!token.isPresent()) {
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

    public void setupPlayerAndCommands(TextToSpeech tts, GatewayDiscordClient client) {
        // setup audio player
        final AudioPlayerManager playerManager = new DefaultAudioPlayerManager();
        playerManager.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);

        AudioSourceManagers.registerRemoteSources(playerManager);
        AudioSourceManagers.registerLocalSource(playerManager);

        final AudioPlayer player = playerManager.createPlayer();

        AudioProvider provider = new LavaPlayerAudioProvider(player);

        // create scheduler
        final TrackScheduler scheduler = new TrackScheduler(player);

        // setup MAKE THE BOT LEAVE command
        makeTheBotLeave = new MakeTheBotLeave(client, player, scheduler);

        // register provider
        commands.put("join", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .then());

        commands.put("play", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .doOnSuccess(voided -> playerManager.loadItem(intent.getIntentContext(), scheduler))
//                .doOnNext(command -> playerManager.loadItem(command, scheduler))
                .then());

        commands.put("search", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .doOnSuccess(voided -> playerManager.loadItem(String.format("ytsearch:%s", intent.getIntentContext()), scheduler))
                .then());

        commands.put("playlist", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
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
                            printString.append(String.format("%d: %s%n", i + 1, audioTrackList.get(i).getInfo().title));
                        }
                    }

                    return messageChannel.createMessage(printString.toString());
                })
                .then());

        // rick
        commands.put("rick", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .doOnSuccess(connection -> playerManager.loadItem("https://www.youtube.com/watch?v=dQw4w9WgXcQ", scheduler))
                .then());

        // fart
        commands.put("fart", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .doOnSuccess(connection -> {
                    playerManager.loadItem(soundDirContents[random.nextInt(soundDirContents.length)].getAbsolutePath(), scheduler);
                })
                .then());

        // sound
        commands.put("sound", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                .doOnSuccess(connection -> playerManager.loadItem("C:\\Users\\sausa\\OneDrive\\Documents\\GitHub\\bob-jr\\src\\main\\resources\\music.opus", scheduler))
                .then());

        // get voices
        commands.put("voices", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(channel -> channel.createMessage(tts.getListOfVoices(intent.getIntentContext())))
                .then());

        commands.put("myvoice-all", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(channel -> {
                    Optional<Member> member = intent.getMessageCreateEvent().getMember();
                    String returnMessage;
                    if(intent.getIntentContext() != null) {
                        String[] voiceParams = intent.getIntentContext().split(" ");
                        String gender = voiceParams.length >= 1 ? voiceParams[0] : null;
                        String voiceName = voiceParams.length >= 2 ? voiceParams[1] : null;
                        Double pitch = voiceParams.length >= 3 ? Double.valueOf(voiceParams[2]) : null;
                        Double speakingRate = voiceParams.length >= 4 ? Double.valueOf(voiceParams[3]) : null;
                        returnMessage = tts.setMemberVoiceConfig(member.get(), gender, voiceName, pitch, speakingRate).toString();
                    } else {
                        returnMessage = tts.getMemberVoiceString(member.get());
                    }
                    return channel.createMessage(returnMessage);
                })
                .then());

        commands.put("myvoice", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(channel -> {
                    Optional<Member> member = intent.getMessageCreateEvent().getMember();
                    String returnMessage;
                    if(intent.getIntentContext() != null) {
                        returnMessage = tts.setMemberVoiceConfig(member.get(), null, intent.getIntentContext().trim(), null, null).toString();
                    } else {
                        returnMessage = String.format(":hear_no_evil: No voice provided! To see list of voices try `@BobJr voices`");
                    }
                    return channel.createMessage(returnMessage);
                })
                .then());

        commands.put("pitch", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(channel -> {
                    final String defaultValue = "DEFAULT_VALUE";
                    Member member = intent.getMessageCreateEvent().getMember().get();
                    double currentPitch = tts.getMemberVoice(member).getAudioConfig().getPitch();
                    final String context = Optional.ofNullable(intent.getIntentContext()).orElse(defaultValue);
                    final StringBuilder stringBuilder = new StringBuilder();
                    switch (context) {
                        case "up":
                            tts.getMemberVoice(member).setPitchInAudioConfigBuilder(Math.min(currentPitch + .5, LimitsHelper.PitchLimits.upperLimit));
                            break;
                        case "down":
                            tts.getMemberVoice(member).setPitchInAudioConfigBuilder(Math.max(currentPitch - .5, LimitsHelper.PitchLimits.lowerLimit));
                            break;
                        case defaultValue:
                            break;
                        default:
                            if(context.matches(RegexHelper.POS_NEG_DECIMAL_REGEX)) {
                                final double doubleValue = Integer.parseInt(context) > 0 ?
                                        Math.min(Double.parseDouble(context), LimitsHelper.PitchLimits.upperLimit) :
                                        Math.max(Double.parseDouble(context), LimitsHelper.PitchLimits.lowerLimit);
                                tts.getMemberVoice(member).setPitchInAudioConfigBuilder(doubleValue);
                            } else {
                                stringBuilder.append(String.format(":no_entry_sign: Invalid input provided.%n"));
                            }
                    }
                    tts.getMemberVoice(member).rebuildAudioConfig();
                    stringBuilder.append(String.format("Current pitch value: %s",
                            tts.getMemberVoice(member).getAudioConfig().getPitch()));
                    return channel.createMessage(stringBuilder.toString());
                })
                .then());

        commands.put("speaking-rate", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(channel -> {
                    final String defaultValue = "DEFAULT_VALUE";
                    Member member = intent.getMessageCreateEvent().getMember().get();
                    double currentSpeakingRate = tts.getMemberVoice(member).getAudioConfig().getSpeakingRate();
                    final String context = Optional.ofNullable(intent.getIntentContext()).orElse(defaultValue);
                    final StringBuilder stringBuilder = new StringBuilder();
                    switch (context) {
                        case "up":
                            tts.getMemberVoice(member).setSpeakingRateInAudioConfigBuilder(Math.min(currentSpeakingRate + .5, LimitsHelper.SpeakingRateLimits.upperLimit));
                            break;
                        case "down":
                            tts.getMemberVoice(member).setSpeakingRateInAudioConfigBuilder(Math.max(currentSpeakingRate - .5, LimitsHelper.SpeakingRateLimits.lowerLimit));
                            break;
                        case defaultValue:
                            break;
                        default:
                            if(context.matches(RegexHelper.POS_DECIMAL_REGEX)) {
                                final double doubleValue = Integer.parseInt(context) > 0 ?
                                        Math.min(Double.parseDouble(context), LimitsHelper.SpeakingRateLimits.upperLimit) :
                                        Math.max(Double.parseDouble(context), LimitsHelper.SpeakingRateLimits.lowerLimit);
                                tts.getMemberVoice(member).setSpeakingRateInAudioConfigBuilder(doubleValue);
                            } else {
                                stringBuilder.append(String.format(":no_entry_sign: Invalid input provided.%n"));
                            }
                    }
                    tts.getMemberVoice(member).rebuildAudioConfig();
                    stringBuilder.append(String.format("Current speaking rate value: %s",
                            tts.getMemberVoice(member).getAudioConfig().getSpeakingRate()));
                    return channel.createMessage(stringBuilder.toString());
                })
                .then());


        // tts
        commands.put("tts", intent -> Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(member -> Mono.just(member)
                    .flatMap(Member::getVoiceState)
                    .flatMap(VoiceState::getChannel)
                    .flatMap(channel -> channel.join(spec -> spec.setProvider(provider)))
                    .flatMap(connection -> tts.synthesisTextMono(member, intent.getIntentContext()))
                    .doOnSuccess(fileLocation -> playerManager.loadItem(fileLocation, scheduler))
                    .then())
                .then());

        // just stop for god sakes
        commands.put("stop", intent -> Mono.justOrEmpty(player)
                .doOnNext(thePlayer -> {
//                    thePlayer.stopTrack();
                    scheduler.clearPlaylist();
                })
                .then());
        commands.put("quit", makeTheBotLeave);
        commands.put("leave", makeTheBotLeave);
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

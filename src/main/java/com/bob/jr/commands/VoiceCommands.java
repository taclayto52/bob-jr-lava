package com.bob.jr.commands;

import com.bob.jr.Intent;
import com.bob.jr.interfaces.ApplicationCommandInterface;
import com.bob.jr.utils.LimitsHelper;
import com.bob.jr.utils.RegexHelper;
import com.bob.jr.utils.ServerResources;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.command.ApplicationCommand;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.discordjson.json.ImmutableApplicationCommandOptionChoiceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bob.jr.utils.ApplicationCommandUtil.getApplicationOptionString;

public class VoiceCommands implements CommandRegistrar {

    private final ServerResources serverResources;
    private final CommandStore commandStore;
    private final Logger logger = LoggerFactory.getLogger(VoiceCommands.class.getName());
    private final Map<String, ApplicationCommandInterface> voiceCommandMap = new HashMap<>();
    private final String TTS_COMMAND_HOOK = "tts";
    private final String VOICES_COMMAND_HOOK = "voices";
    private final String PITCH_COMMAND_HOOK = "pitch";

    private final String TTS_COMMAND_TEXT_OPTION = "tts_text";
    private final String VOICES_COMMAND_LANGUAGE_OPTION = "voices_language";
    private final String PITCH_COMMAND_SET_OPTION = "pitch_set";

    private final String PITCH_COMMAND_OPTION_UP = "pitch_up";
    private final String PITCH_COMMAND_OPTION_DOWN = "pitch_down";

    public VoiceCommands(ServerResources serverResources, CommandStore commandStore) {
        this.serverResources = serverResources;
        this.commandStore = commandStore;
    }

    public Disposable registerPitchCommand() {
        final var pitchUpOption = ImmutableApplicationCommandOptionChoiceData.builder()
                .name(PITCH_COMMAND_OPTION_UP)
                .value(PITCH_COMMAND_OPTION_UP).build();
        final var pitchDownOption = ImmutableApplicationCommandOptionChoiceData.builder()
                .name(PITCH_COMMAND_OPTION_DOWN)
                .value(PITCH_COMMAND_OPTION_DOWN).build();
        final var pitchCommandOption = ApplicationCommandOptionData.builder()
                .name(PITCH_COMMAND_SET_OPTION)
                .description("Direction to set pitch.")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .choices(List.of(pitchUpOption, pitchDownOption))
                .build();

        final var pitchApplicationCommand = ApplicationCommandRequest.builder()
                .name(PITCH_COMMAND_HOOK)
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .description("Set your TTS pitch")
                .addAllOptions(List.of(pitchCommandOption))
                .build();

        voiceCommandMap.put(PITCH_COMMAND_HOOK, this::pitchCommand);
        return commandStore.registerCommand(pitchApplicationCommand);
    }

    public Disposable registerTtsCommand() {
        final var playCommandOption = ApplicationCommandOptionData.builder()
                .name(TTS_COMMAND_TEXT_OPTION)
                .description("Text to turn to speech.")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .required(true)
                .build();

        final ApplicationCommandRequest ttsApplicationCommand = ApplicationCommandRequest.builder()
                .name(TTS_COMMAND_HOOK)
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .description("Play an audio source")
                .addAllOptions(List.of(playCommandOption))
                .build();

        voiceCommandMap.put(TTS_COMMAND_HOOK, this::ttsCommand);
        return commandStore.registerCommand(ttsApplicationCommand);
    }

    public Disposable registerVoicesCommand() {
        final var voicesLanguageOption = ApplicationCommandOptionData.builder()
                .name(VOICES_COMMAND_LANGUAGE_OPTION)
                .description("Language to list voices in (defaults to us_en).")
                .type(ApplicationCommandOption.Type.STRING.getValue())
                .required(false)
                .build();

        final var voicesApplicationCommand = ApplicationCommandRequest.builder()
                .name(VOICES_COMMAND_HOOK)
                .type(ApplicationCommand.Type.CHAT_INPUT.getValue())
                .description("Get list of available voices.")
                .addAllOptions(List.of(voicesLanguageOption))
                .build();

        voiceCommandMap.put(VOICES_COMMAND_HOOK, this::voicesCommand);
        return commandStore.registerCommand(voicesApplicationCommand);
    }

    public Mono<Void> voicesCommand(Intent intent) {
        final var channel = intent.getMessageCreateEvent().getMessage().getChannel().blockOptional().orElseThrow();
        return voicesFunction(channel, intent.getIntentContext());
    }

    public Mono<Void> voicesCommand(ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        final var channel = applicationCommandInteractionEvent.getInteraction().getChannel().blockOptional().orElseThrow();
        return voicesFunction(channel, getApplicationOptionString(applicationCommandInteractionEvent, VOICES_COMMAND_LANGUAGE_OPTION));
    }

    public Mono<Void> voicesFunction(final MessageChannel messageChannel, final String messageContext) {
        return Mono.just(messageChannel.createMessage(serverResources.getTextToSpeech().getListOfVoices(messageContext)))
                .then();
    }

    public Mono<Void> myVoiceAll(Intent intent) {
        final var tts = serverResources.getTextToSpeech();
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(channel -> {
                    Optional<Member> member = intent.getMessageCreateEvent().getMember();
                    String returnMessage;
                    if (intent.getIntentContext() != null) {
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
                .then();
    }

    public Mono<Void> myVoice(Intent intent) {
        final var tts = serverResources.getTextToSpeech();
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(channel -> {
                    Optional<Member> member = intent.getMessageCreateEvent().getMember();
                    String returnMessage;
                    if (intent.getIntentContext() != null) {
                        returnMessage = tts.setMemberVoiceConfig(member.get(), null, intent.getIntentContext().trim(), null, null).toString();
                    } else {
                        returnMessage = String.format(":hear_no_evil: No voice provided! To see list of voices try `@BobJr voices`");
                    }
                    return channel.createMessage(returnMessage);
                })
                .then();
    }

    public Mono<Void> pitchCommand(Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(channel -> {
                    Member member = intent.getMessageCreateEvent().getMember().get();
                    return pitchFunction(member, channel, Optional.ofNullable(intent.getIntentContext()));
                })
                .then();
    }

    public Mono<Void> pitchCommand(final ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        return Mono.justOrEmpty(applicationCommandInteractionEvent.getInteraction().getChannel())
                .flatMap(channel -> {
                    final var member = applicationCommandInteractionEvent.getInteraction().getMember().get();
                    final var pitchOption = getApplicationOptionString(applicationCommandInteractionEvent, PITCH_COMMAND_SET_OPTION);
                    return pitchFunction(member, channel.block(), Optional.ofNullable(pitchOption));
                })
                .then();
    }

    public Mono<Void> pitchFunction(final Member member, final MessageChannel channel, final Optional<String> pitchValueOp) {
        final String defaultValue = "DEFAULT_VALUE";
        final String pitchValue = pitchValueOp.orElse(defaultValue);
        final var tts = serverResources.getTextToSpeech();

        final double currentPitch = tts.getMemberVoice(member).getAudioConfig().getPitch();

        final StringBuilder stringBuilder = new StringBuilder();
        switch (pitchValue) {
            case "up":
                tts.getMemberVoice(member).setPitchInAudioConfigBuilder(Math.min(currentPitch + .5, LimitsHelper.PitchLimits.upperLimit));
                break;
            case "down":
                tts.getMemberVoice(member).setPitchInAudioConfigBuilder(Math.max(currentPitch - .5, LimitsHelper.PitchLimits.lowerLimit));
                break;
            case defaultValue:
                break;
            default:
                if (pitchValue.matches(RegexHelper.POS_NEG_DECIMAL_REGEX)) {
                    final double doubleValue = Double.parseDouble(pitchValue) > 0 ?
                            Math.min(Double.parseDouble(pitchValue), LimitsHelper.PitchLimits.upperLimit) :
                            Math.max(Double.parseDouble(pitchValue), LimitsHelper.PitchLimits.lowerLimit);
                    tts.getMemberVoice(member).setPitchInAudioConfigBuilder(doubleValue);
                } else {
                    stringBuilder.append(String.format(":no_entry_sign: Invalid input provided.%n"));
                }
        }
        tts.getMemberVoice(member).rebuildAudioConfig();
        stringBuilder.append(String.format("Current pitch value: %s",
                tts.getMemberVoice(member).getAudioConfig().getPitch()));
        return channel.createMessage(stringBuilder.toString()).then();
    }

    public Mono<Void> speakingRate(Intent intent) {
        final var tts = serverResources.getTextToSpeech();
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
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
                            if (context.matches(RegexHelper.POS_DECIMAL_REGEX)) {
                                final double doubleValue = Double.parseDouble(context) > 0 ?
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
                .then();
    }

    public Mono<Void> ttsCommand(Intent intent) {
        // join channel
        final var member = intent.getMessageCreateEvent().getMember().orElseThrow();

        return ttsFunction(member, intent.getIntentContext(), true);
    }

    public Mono<Void> ttsCommand(ApplicationCommandInteractionEvent applicationCommandInteractionEvent) {
        final var member = applicationCommandInteractionEvent.getInteraction().getMember().orElseThrow();

        // TODO @tclayton update this to conditionally join the channel
        return ttsFunction(member, getApplicationOptionString(applicationCommandInteractionEvent, TTS_COMMAND_TEXT_OPTION), true);
    }

    public Mono<Void> ttsFunction(Member member, String tts, boolean joinChannel) {
        if (joinChannel) {
            member.getVoiceState()
                    .flatMap(VoiceState::getChannel)
                    .flatMap(VoiceChannel::join)
                    .block();
        }


        final var ttsService = serverResources.getTextToSpeech();
        return Mono.justOrEmpty(ttsService.synthesizeTextMono(member, tts).block())
                .doOnSuccess(fileLocation -> serverResources.getAudioPlayerManager().loadItem(fileLocation, serverResources.getTrackScheduler()))
                .then();
    }

    public Mono<Void> tts(Intent intent) {
        final var tts = serverResources.getTextToSpeech();
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMember())
                .flatMap(member -> Mono.just(member)
                        .flatMap(Member::getVoiceState)
                        .flatMap(VoiceState::getChannel)
                        .flatMap(channel -> channel.join(spec -> spec.setProvider(serverResources.getServerAudioProvider())))
                        .flatMap(connection -> tts.synthesizeTextMono(member, intent.getIntentContext()))
                        .doOnSuccess(fileLocation -> serverResources.getAudioPlayerManager().loadItem(fileLocation, serverResources.getTrackScheduler()))
                        .then())
                .then();
    }

    @Override
    public Disposable registerCommands() {
        return Flux.fromIterable(List.of(registerTtsCommand(), registerVoicesCommand(), registerPitchCommand()))
                .doOnComplete(() -> logger.info("Finished registering {}", VoiceCommands.class.getName()))
                .blockLast();
    }
}

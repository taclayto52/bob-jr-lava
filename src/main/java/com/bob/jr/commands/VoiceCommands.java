package com.bob.jr.commands;

import com.bob.jr.Intent;
import com.bob.jr.utils.LimitsHelper;
import com.bob.jr.utils.RegexHelper;
import com.bob.jr.utils.ServerResources;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import reactor.core.publisher.Mono;

import java.util.Optional;

public class VoiceCommands {

    private final ServerResources serverResources;

    public VoiceCommands(ServerResources serverResources) {
        this.serverResources = serverResources;
    }

    public Mono<Void> voices(Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
                .flatMap(Message::getChannel)
                .flatMap(channel -> channel.createMessage(serverResources.getTextToSpeech().getListOfVoices(intent.getIntentContext())))
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

    public Mono<Void> pitch(Intent intent) {
        final var tts = serverResources.getTextToSpeech();
        return Mono.justOrEmpty(intent.getMessageCreateEvent().getMessage())
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
                            if (context.matches(RegexHelper.POS_NEG_DECIMAL_REGEX)) {
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
                .then();
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

    public Mono<Void> saveVoice(Intent intent) {
        final var tts = serverResources.getTextToSpeech();
        return Mono.justOrEmpty(intent.getMessageCreateEvent())
                .flatMap(messageCreateEvent -> {
                    final var messageChannelOptional = messageCreateEvent.getMessage().getChannel().blockOptional();
                    final var messageChannel = messageChannelOptional.orElseThrow();
                    messageChannel.type();
                    if (messageCreateEvent.getMember().isEmpty()) {
                        return messageChannel.createMessage(String.format("No member found in message event :confused: %n(Get yourself checked)"));
                    } else {
                        final var member = messageCreateEvent.getMember().get();
                        final var storageSuccessful = tts.persistMemberConfigToStorage(member).block();
                        var storeMessage = String.format("%s Storage %s for %s.",
                                storageSuccessful ? ":white_check_mark:" : ":x:",
                                storageSuccessful ? "successful" : "FAILED",
                                member.getUsername());
                        return messageChannel.createMessage(storeMessage);
                    }
                })
                .then();
    }

}

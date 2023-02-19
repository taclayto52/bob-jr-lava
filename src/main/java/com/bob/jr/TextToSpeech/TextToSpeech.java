package com.bob.jr.TextToSpeech;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import discord4j.core.object.entity.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TextToSpeech {

    private static final Map<Member, MemberVoiceConfig> memberVoiceMap = new HashMap<>();
    private final TextToSpeechClient textToSpeechClient;
    private final Logger logger = LoggerFactory.getLogger(TextToSpeech.class);

    public TextToSpeech() throws IOException {
        textToSpeechClient = TextToSpeechClient.create();
    }

    public Mono<String> synthesizeTextMono(final Member member, final String text) {
        return Mono.just(synthesizeText(member, text));
    }

    public String synthesizeText(final String text) {
        return synthesizeText(null, text);
    }

    public String synthesizeText(final Member member, final String text) {
        final MemberVoiceConfig memberVoiceConfig = member != null ? getMemberVoice(member) : new MemberVoiceConfig();

        final SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
        final VoiceSelectionParams voice = memberVoiceConfig.getVoiceSelectionParams();
        final AudioConfig audioConfig = memberVoiceConfig.getAudioConfig();

        final SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

        final ByteString audioContents = response.getAudioContent();

        if (response.getAudioContent().isEmpty()) {
            return this.getClass().getClassLoader().getResource("soundFiles/oom.opus").getFile();
        }

        final OutputStream out;
        String fileLocation = null;
        try {
            final File file = new File("output.opus");
            fileLocation = file.getPath();
            out = new FileOutputStream(file);
            out.write(audioContents.toByteArray());
            System.out.println("Audio content written to file \"output.opus\"");
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        } catch (final IOException e) {
            e.printStackTrace();
        }

        if (fileLocation.isEmpty()) {
            fileLocation = this.getClass().getClassLoader().getResource("soundFiles/ERROR.opus").getFile();
        }

        return fileLocation;
    }

    public String getListOfVoices(final String language) {
        final String finalLanguage = Optional.ofNullable(language).orElse("en-us");

        logger.info(String.format("getting list of voices for language: %s", finalLanguage));
        final ListVoicesResponse listVoicesResponse = textToSpeechClient.listVoices(finalLanguage);
        return listVoicesResponse.getVoicesList()
                .stream()
                .map(Voice::getName)
                .reduce("", (accu, voiceName) -> String.format("%s%s%s", accu, accu != "" ? "," : "", voiceName));

    }

    public MemberVoiceConfig setMemberVoiceConfig(final Member member, final String gender, final String voiceName, final Double pitch, final Double speakingRate) {
        final MemberVoiceConfig memberVoiceConfig = getMemberVoice(member);
        memberVoiceConfig.setGenderInVoiceSelectionParams(gender);
        memberVoiceConfig.setVoiceNameInVoiceSelectionParams(voiceName);
        memberVoiceConfig.setPitchInAudioConfigBuilder(pitch);
        memberVoiceConfig.setSpeakingRateInAudioConfigBuilder(speakingRate);

        memberVoiceConfig.rebuildAll();
        return memberVoiceConfig;
    }

    public MemberVoiceConfig getMemberVoice(final Member member) {
        final Optional<MemberVoiceConfig> optionalMemberVoiceConfig = Optional.ofNullable(memberVoiceMap.putIfAbsent(member, new MemberVoiceConfig()));
        return optionalMemberVoiceConfig.orElse(memberVoiceMap.get(member));
    }

    public String getMemberVoiceString(final Member member) {
        return getMemberVoice(member).toString();
    }

}

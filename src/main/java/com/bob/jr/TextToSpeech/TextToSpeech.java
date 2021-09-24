package com.bob.jr.TextToSpeech;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import discord4j.core.object.entity.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class TextToSpeech {

    private static final Map<Member, MemberVoiceConfig> memberVoiceMap = new HashMap<>();
    private final TextToSpeechClient textToSpeechClient;
    private final Logger logger = LoggerFactory.getLogger(TextToSpeech.class);

    public TextToSpeech() throws IOException {
        textToSpeechClient = TextToSpeechClient.create();
    }

    public Mono<String> synthesisTextMono(Member member, String text) {
        return Mono.just(synthesisText(member, text));
    }

    public String synthesisText(String text) {
        return synthesisText(null, text);
    }

    public String synthesisText(Member member, String text) {
        MemberVoiceConfig memberVoiceConfig = member != null ? getMemberVoice(member) : new MemberVoiceConfig();

        SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
        VoiceSelectionParams voice = memberVoiceConfig.getVoiceSelectionParams();
        AudioConfig audioConfig = memberVoiceConfig.getAudioConfig();

        SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

        ByteString audioContents = response.getAudioContent();

        if(response.getAudioContent().isEmpty()) {
            return this.getClass().getClassLoader().getResource("oom.opus").getFile();
        }

        OutputStream out;
        String fileLocation = null;
        try  {
            File file = new File("output.opus");
            fileLocation = file.getPath();
            out = new FileOutputStream(file);
            out.write(audioContents.toByteArray());
            System.out.println("Audio content written to file \"output.opus\"");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(fileLocation.isEmpty()) {
            fileLocation = this.getClass().getClassLoader().getResource("ERROR.opus").getFile();
        }

        return fileLocation;
    }

    public String getListOfVoices(String language) {
        String finalLanguage = Optional.ofNullable(language).orElse("en-us");

        logger.info(String.format("getting list of voices for language: %s", finalLanguage));
        ListVoicesResponse listVoicesResponse = textToSpeechClient.listVoices(finalLanguage);
        return listVoicesResponse.getVoicesList()
                .stream()
                .map(Voice::getName)
                .reduce("", (accu, voiceName) -> String.format("%s%s%s", accu, accu != "" ? "," : "", voiceName));

    }

    public MemberVoiceConfig setMemberVoiceConfig(Member member, String gender, String voiceName, Double pitch, Double speakingRate) {
        MemberVoiceConfig memberVoiceConfig = getMemberVoice(member);
        memberVoiceConfig.setGenderInVoiceSelectionParams(gender);
        memberVoiceConfig.setVoiceNameInVoiceSelectionParams(voiceName);
        memberVoiceConfig.setPitchInAudioConfigBuilder(pitch);
        memberVoiceConfig.setSpeakingRateInAudioConfigBuilder(speakingRate);

        memberVoiceConfig.rebuildAll();
        return memberVoiceConfig;
    }

    public MemberVoiceConfig getMemberVoice(Member member) {
        Optional<MemberVoiceConfig> optionalMemberVoiceConfig = Optional.ofNullable(memberVoiceMap.putIfAbsent(member, new MemberVoiceConfig()));
        return optionalMemberVoiceConfig.orElse(memberVoiceMap.get(member));
    }

    public String getMemberVoiceString(Member member) {
        return getMemberVoice(member).toString();
    }

}

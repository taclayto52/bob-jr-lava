package com.bob.jr;

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

    private static final String defaultVoiceString = "en-US-Standard-A";
    private static final VoiceSelectionParams.Builder defaultVoiceSelectionParamsBuilder = VoiceSelectionParams.newBuilder()
            .setLanguageCode("en-us")
            .setName(defaultVoiceString)
            .setSsmlGender(SsmlVoiceGender.NEUTRAL);
    private static final VoiceSelectionParams defaultVoiceSelectionParams = defaultVoiceSelectionParamsBuilder.build();
    private static final AudioConfig.Builder defaultAudioConfigBuilder = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.OGG_OPUS);
    private static final AudioConfig defaultAudioConfig = defaultAudioConfigBuilder.build();

    private static final Map<Member, MemberVoiceConfig> memberVoiceMap = new HashMap<>();
    private TextToSpeechClient textToSpeechClient;
    private Logger logger = LoggerFactory.getLogger(TextToSpeech.class);

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

    static class MemberVoiceConfig {

        private VoiceSelectionParams voiceSelectionParams;
        private AudioConfig audioConfig;
        private VoiceSelectionParams.Builder voiceSelectionParamsBuilder;
        private AudioConfig.Builder audioConfigBuilder;

        public MemberVoiceConfig() {
            this(defaultVoiceSelectionParams, defaultAudioConfig);
        }

        public MemberVoiceConfig(VoiceSelectionParams voiceSelectionParams, AudioConfig audioConfig) {
            this.voiceSelectionParams = voiceSelectionParams;
            this.audioConfig = audioConfig;
            this.voiceSelectionParamsBuilder = defaultVoiceSelectionParamsBuilder.clone();
            this.audioConfigBuilder = defaultAudioConfigBuilder.clone();
        }

        public VoiceSelectionParams getVoiceSelectionParams() {
            return voiceSelectionParams;
        }

        public void setGenderInVoiceSelectionParams(String gender) {
            Optional<String> genderOptional = Optional.ofNullable(gender);

            genderOptional.ifPresent(genderPres -> {
                SsmlVoiceGender ssmlVoiceGender;
                switch (genderPres.toLowerCase(Locale.ENGLISH)) {
                    case "male": ssmlVoiceGender = SsmlVoiceGender.MALE; break;
                    case "female": ssmlVoiceGender = SsmlVoiceGender.FEMALE; break;
                    default: ssmlVoiceGender = SsmlVoiceGender.NEUTRAL; break;
                }
                voiceSelectionParamsBuilder.setSsmlGender(ssmlVoiceGender);
            });
        }

        public void setVoiceNameInVoiceSelectionParams(String voiceName) {
            Optional<String> voiceNameOptional = Optional.ofNullable(voiceName);
            voiceNameOptional.ifPresent(voiceNamePres -> voiceSelectionParamsBuilder.setName(voiceNamePres));
        }

        public void setPitchInAudioConfigBuilder(Double pitch) {
            Optional<Double> pitchOptional = Optional.ofNullable(pitch);
            pitchOptional.ifPresent(pitchPres -> audioConfigBuilder.setPitch(pitchPres));
        }

        public void setSpeakingRateInAudioConfigBuilder(Double speakingRate) {
            Optional<Double> speakingRateOptional = Optional.ofNullable(speakingRate);
            speakingRateOptional.ifPresent(speakingRatePres -> audioConfigBuilder.setSpeakingRate(speakingRate));
        }

        public void rebuildAudioConfig() {
            this.audioConfig = this.audioConfigBuilder.build();
        }

        public void rebuildVoiceSelectionParamsBuilder() {
            this.voiceSelectionParams = this.voiceSelectionParamsBuilder.build();
        }

        public void rebuildAll() {
            rebuildAudioConfig();
            rebuildVoiceSelectionParamsBuilder();
        }

        public void setVoiceSelectionParams(VoiceSelectionParams voiceSelectionParams) {
            this.voiceSelectionParams = voiceSelectionParams;
        }

        public AudioConfig getAudioConfig() {
            return audioConfig;
        }

        public void setAudioConfig(AudioConfig audioConfig) {
            this.audioConfig = audioConfig;
        }

        @Override
        public String toString() {
            return String.format("gender: %s%n" +
                            "voice: %s%n" +
                            "pitch: %f%n" +
                            "speaking rate: %f",
                    voiceSelectionParams.getSsmlGender().getValueDescriptor(), voiceSelectionParams.getName(), audioConfig.getPitch(), audioConfig.getSpeakingRate());
        }
    }

}

package com.bob.jr.TextToSpeech;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;

import java.util.Locale;
import java.util.Optional;

public class MemberVoiceConfig {

    private VoiceSelectionParams voiceSelectionParams;
    private AudioConfig audioConfig;
    private final VoiceSelectionParams.Builder voiceSelectionParamsBuilder;
    private final AudioConfig.Builder audioConfigBuilder;

    private static final String defaultVoiceString = "en-US-Standard-A";
    private static final VoiceSelectionParams.Builder defaultVoiceSelectionParamsBuilder = VoiceSelectionParams.newBuilder()
            .setLanguageCode("en-us")
            .setName(defaultVoiceString)
            .setSsmlGender(SsmlVoiceGender.NEUTRAL);
    private static final VoiceSelectionParams defaultVoiceSelectionParams = defaultVoiceSelectionParamsBuilder.build();
    private static final AudioConfig.Builder defaultAudioConfigBuilder = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.OGG_OPUS);
    private static final AudioConfig defaultAudioConfig = defaultAudioConfigBuilder.build();

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

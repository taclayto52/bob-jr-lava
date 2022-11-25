package com.bob.jr.TextToSpeech;

import com.google.cloud.texttospeech.v1.AudioConfig;
import com.google.cloud.texttospeech.v1.AudioEncoding;
import com.google.cloud.texttospeech.v1.SsmlVoiceGender;
import com.google.cloud.texttospeech.v1.VoiceSelectionParams;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.buffer.MessageBuffer;
import org.msgpack.value.ValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

public class MemberVoiceConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemberVoiceConfig.class);
    private static final String defaultVoiceString = "en-US-Standard-A";
    private static final VoiceSelectionParams.Builder defaultVoiceSelectionParamsBuilder = VoiceSelectionParams.newBuilder()
            .setLanguageCode("en-us")
            .setName(defaultVoiceString)
            .setSsmlGender(SsmlVoiceGender.NEUTRAL);
    private static final VoiceSelectionParams defaultVoiceSelectionParams = defaultVoiceSelectionParamsBuilder.build();
    private static final AudioConfig.Builder defaultAudioConfigBuilder = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.OGG_OPUS);
    private static final AudioConfig defaultAudioConfig = defaultAudioConfigBuilder.build();

    transient private final VoiceSelectionParams.Builder voiceSelectionParamsBuilder;
    transient private final AudioConfig.Builder audioConfigBuilder;
    private VoiceSelectionParams voiceSelectionParams;
    private AudioConfig audioConfig;

    public MemberVoiceConfig() {
        this(Optional.of(defaultVoiceSelectionParams), Optional.of(defaultAudioConfig));
    }

    public MemberVoiceConfig(final Optional<VoiceSelectionParams> voiceSelectionParamsOptional, final Optional<AudioConfig> audioConfigOptional) {
        final var voiceSelectionParams = voiceSelectionParamsOptional.orElse(defaultVoiceSelectionParams);
        final var audioConfig = audioConfigOptional.orElse(defaultAudioConfig);

        this.voiceSelectionParamsBuilder = defaultVoiceSelectionParamsBuilder.clone().mergeFrom(voiceSelectionParams);
        this.audioConfigBuilder = defaultAudioConfigBuilder.clone().mergeFrom(audioConfig);
        this.voiceSelectionParams = voiceSelectionParamsBuilder.build();
        this.audioConfig = audioConfigBuilder.build();
    }

    public static MemberVoiceConfig unwrapMessageBuffer(final ByteArrayInputStream byteArrayInputStream) throws IOException {
        final var unpacker = MessagePack.newDefaultUnpacker(byteArrayInputStream);
        var paramCount = 0;

        Optional<VoiceSelectionParams> voiceSelectionParamsOptional = Optional.empty();
        Optional<AudioConfig> audioConfigOptional = Optional.empty();
        var nilReached = false;
        while (unpacker.hasNext() && !nilReached) {
            final var format = unpacker.getNextFormat();
            final var value = unpacker.unpackValue();

            if (format.getValueType() == ValueType.NIL) {
                nilReached = true;
            } else {
                final var binaryValue = value.asBinaryValue().asByteArray();
                switch (paramCount) {
                    case 0 -> voiceSelectionParamsOptional = Optional.of(VoiceSelectionParams.parseFrom(binaryValue));
                    case 1 -> audioConfigOptional = Optional.of(AudioConfig.parseFrom(binaryValue));
                    default -> throw new IOException("Unexpected data format; abandoning unpack.");
                }
                paramCount++;
            }
        }

        return new MemberVoiceConfig(voiceSelectionParamsOptional, audioConfigOptional);
    }

    public VoiceSelectionParams getVoiceSelectionParams() {
        return voiceSelectionParams;
    }

    public void setGenderInVoiceSelectionParams(final String gender) {
        Optional<String> genderOptional = Optional.ofNullable(gender);

        genderOptional.ifPresent(genderPres -> {
            SsmlVoiceGender ssmlVoiceGender;
            switch (genderPres.toLowerCase(Locale.ENGLISH)) {
                case "male":
                    ssmlVoiceGender = SsmlVoiceGender.MALE;
                    break;
                case "female":
                    ssmlVoiceGender = SsmlVoiceGender.FEMALE;
                    break;
                default:
                    ssmlVoiceGender = SsmlVoiceGender.NEUTRAL;
                    break;
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

    public MessageBuffer toMessageBuffer() throws IOException {
        rebuildAll();

        final MessageBufferPacker packer = MessagePack.newDefaultBufferPacker();
        packBinaryMessageBufferData(packer, this.voiceSelectionParams.toByteArray());
        packBinaryMessageBufferData(packer, this.audioConfig.toByteArray());
        try {
            packer.packNil();
            packer.close();
        } catch (IOException ioException) {
            LOGGER.error(String.format("Error writing message buffer %s", ioException.getMessage()));
            // throwing to bubble error back up to user
            throw ioException;
        }
        return packer.toMessageBuffer();
    }

    private void packBinaryMessageBufferData(final MessageBufferPacker packer,
                                             final byte[] binary) throws IOException {
        try {
            packer.packBinaryHeader(binary.length);
            packer.writePayload(binary);
        } catch (IOException ioException) {
            LOGGER.error(String.format("Error writing message buffer %s", ioException.getMessage()));
            // throwing to bubble error back up to user
            throw ioException;
        }

    }

}

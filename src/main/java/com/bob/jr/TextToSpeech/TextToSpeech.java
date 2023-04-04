package com.bob.jr.TextToSpeech;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TextToSpeech {

    private static final Map<Snowflake, MemberVoiceConfig> memberVoiceMap = new HashMap<>();
    private static final String NEARLINE_BOBJR_SETTINGS = "nearline-bobjr-settings";
    private static final String MEMBER_VOICE_SETTINGS_FOLDER = "member-voice-settings";
    private final TextToSpeechClient textToSpeechClient;
    private final Logger logger = LoggerFactory.getLogger(TextToSpeech.class);

    private final Optional<Storage> gStorageOptional;

    public TextToSpeech() throws IOException {
        this(Optional.empty());
    }

    public TextToSpeech(Optional<Storage> gStorageOptional) throws IOException {
        textToSpeechClient = TextToSpeechClient.create();
        this.gStorageOptional = gStorageOptional;
        retrieveVoiceSettingsFromStorage(gStorageOptional);
    }

    private void retrieveVoiceSettingsFromStorage(Optional<Storage> gStorageOptional) {
        final var loadFromStorage = true;
        if (gStorageOptional.isPresent()) {
            if (!loadFromStorage) {
                return;
            }
            // read in any user data in the bucket
            final var voiceSettingsPages = gStorageOptional.get().list(NEARLINE_BOBJR_SETTINGS, Storage.BlobListOption.currentDirectory(), Storage.BlobListOption.prefix(MEMBER_VOICE_SETTINGS_FOLDER + "/"));
            // read voice settings
            voiceSettingsPages.iterateAll().forEach((blob) -> {
                if (blob.isDirectory()) {
                    return;
                }

                String blobName = blob.getName();
                if (blobName.endsWith("/")) {
                    return;
                }

                final var memberId = blobName.split("/")[1];
                final var byteArrayInputStream = new ByteArrayInputStream(blob.getContent());
                try {
                    final var memberVoiceConfig = MemberVoiceConfig.unwrapMessageBuffer(byteArrayInputStream);
                    setMemberVoiceConfigDirect(Snowflake.of(memberId), memberVoiceConfig);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public Mono<String> synthesizeTextMono(Member member, String text) {
        return Mono.just(synthesizeText(member, text));
    }

    public String synthesizeText(String text) {
        return synthesizeText(null, text);
    }

    public String synthesizeText(Member member, String text) {
        MemberVoiceConfig memberVoiceConfig = member != null ? getMemberVoice(member) : new MemberVoiceConfig();

        SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
        VoiceSelectionParams voice = memberVoiceConfig.getVoiceSelectionParams();
        AudioConfig audioConfig = memberVoiceConfig.getAudioConfig();

        SynthesizeSpeechResponse response = textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

        ByteString audioContents = response.getAudioContent();

        if (response.getAudioContent().isEmpty()) {
            return this.getClass().getClassLoader().getResource("oom.opus").getFile();
        }

        OutputStream out;
        String fileLocation = null;
        try {
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

        if (fileLocation.isEmpty()) {
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
        final var memberId = member.getId();
        Optional<MemberVoiceConfig> optionalMemberVoiceConfig = Optional.ofNullable(memberVoiceMap.putIfAbsent(memberId, new MemberVoiceConfig()));
        return optionalMemberVoiceConfig.orElse(memberVoiceMap.get(memberId));
    }


    // used for voice loading only
    public void setMemberVoiceConfigDirect(Snowflake memberId, MemberVoiceConfig memberVoiceConfig) {
        memberVoiceMap.put(memberId, memberVoiceConfig);
    }

    public String getMemberVoiceString(Member member) {
        return getMemberVoice(member).toString();
    }

    public Mono<Boolean> persistMemberConfigToStorage(Member member) {
        if (gStorageOptional.isEmpty()) {
            return Mono.just(true);
        } else {
            final var gStorage = gStorageOptional.get();
            final BlobInfo blobInfo = BlobInfo.newBuilder(NEARLINE_BOBJR_SETTINGS, MEMBER_VOICE_SETTINGS_FOLDER + "/" + member.getId().asString()).build();


            final var memberVoiceConfig = getMemberVoice(member);
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            boolean storageSuccessful = false;
            try {
                byteArrayOutputStream.writeBytes(memberVoiceConfig.toMessageBuffer().array());

                gStorage.create(blobInfo, byteArrayOutputStream.toByteArray());
                storageSuccessful = true;
            } catch (StorageException storageException) {
                logger.error(String.format("Storage exception: %s", storageException.getMessage()));
                return Mono.error(storageException);
            } catch (IOException exception) {
                return Mono.error(exception);
            }
            return Mono.just(storageSuccessful);
        }
    }

}

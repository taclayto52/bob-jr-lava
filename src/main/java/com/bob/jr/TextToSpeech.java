package com.bob.jr;

import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import reactor.core.publisher.Mono;

import java.io.*;

public class TextToSpeech {

    private TextToSpeechClient textToSpeechClient;

    public TextToSpeech() throws IOException {
        textToSpeechClient = TextToSpeechClient.create();
    }

    public Mono<String> synthesisTextMono(String text) {
        return Mono.just(synthesisText(text));
    }

    public String synthesisText(String text) {
        SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();
        VoiceSelectionParams voice = VoiceSelectionParams.newBuilder()
                .setLanguageCode("en-US")
                .setSsmlGender(SsmlVoiceGender.NEUTRAL)
                .build();

        AudioConfig audioConfig = AudioConfig.newBuilder().setAudioEncoding(AudioEncoding.OGG_OPUS).build();

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

}

package com.bob.jr.utils;

import com.bob.jr.TextToSpeech.TextToSpeech;
import com.bob.jr.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import discord4j.core.GatewayDiscordClient;
import discord4j.voice.AudioProvider;

public record ServerResources(AudioProvider serverAudioProvider,
                              TrackScheduler trackScheduler,
                              GatewayDiscordClient gatewayClient,
                              AudioPlayer audioPlayer,
                              AudioPlayerManager audioPlayerManager,
                              TextToSpeech textToSpeech,
                              AudioTrackCache audioTrackCache) {

    public String handleFile(final String fileName) {
        final var resource = getClass().getClassLoader().getResource("soundFiles/" + fileName);
        return resource != null
                ? resource.getFile() // file found in classpath
                : "/opt/bob-jr/soundFiles/" + fileName;
    }
}

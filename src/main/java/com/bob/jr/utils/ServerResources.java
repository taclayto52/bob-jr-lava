package com.bob.jr.utils;

import com.bob.jr.TextToSpeech.TextToSpeech;
import com.bob.jr.TrackScheduler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import discord4j.core.GatewayDiscordClient;
import discord4j.voice.AudioProvider;

public class ServerResources {

    private final AudioProvider serverAudioProvider;
    private final TrackScheduler trackScheduler;
    private final GatewayDiscordClient gatewayClient;
    private final AudioPlayer audioPlayer;
    private final AudioPlayerManager audioPlayerManager;
    private final TextToSpeech textToSpeech;
    private final AudioTrackCache audioTrackCache;

    public ServerResources(final AudioProvider serverAudioProvider,
                           final TrackScheduler trackScheduler,
                           final GatewayDiscordClient gatewayClient,
                           final AudioPlayer audioPlayer,
                           final AudioPlayerManager audioPlayerManager,
                           final TextToSpeech textToSpeech,
                           final AudioTrackCache audioTrackCache) {
        this.serverAudioProvider = serverAudioProvider;
        this.trackScheduler = trackScheduler;
        this.gatewayClient = gatewayClient;
        this.audioPlayer = audioPlayer;
        this.audioPlayerManager = audioPlayerManager;
        this.textToSpeech = textToSpeech;
        this.audioTrackCache = audioTrackCache;
    }

    public AudioProvider getServerAudioProvider() {
        return serverAudioProvider;
    }

    public TrackScheduler getTrackScheduler() {
        return trackScheduler;
    }

    public GatewayDiscordClient getGatewayDiscordClient() {
        return gatewayClient;
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }

    public AudioPlayerManager getAudioPlayerManager() {
        return audioPlayerManager;
    }

    public TextToSpeech getTextToSpeech() {
        return textToSpeech;
    }

    public AudioTrackCache getAudioTrackCache() {
        return audioTrackCache;
    }

    public String handleFile(final String fileName) {
        final var resource = getClass().getClassLoader().getResource("soundFiles/" + fileName);
        return resource != null
                ? resource.getFile() // file found in classpath
                : "/opt/bob-jr/soundFiles/" + fileName;
    }
}

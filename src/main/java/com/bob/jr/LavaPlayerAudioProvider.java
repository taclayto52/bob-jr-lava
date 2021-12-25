package com.bob.jr;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import discord4j.voice.AudioProvider;

import java.nio.ByteBuffer;

public class LavaPlayerAudioProvider extends AudioProvider {

    private final AudioPlayer player;
    private final AudioPlayer announcementPlayer;
    private final MutableAudioFrame frame = new MutableAudioFrame();

    public LavaPlayerAudioProvider(final AudioPlayer player, AudioPlayer announcementPlayer) {
        super(ByteBuffer.allocate(StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize()));

        frame.setBuffer(getBuffer());
        this.player = player;
        this.announcementPlayer = announcementPlayer;
    }

    @Override
    public boolean provide() {
        boolean didProvide;
        if (player.isPaused()) {
            didProvide = announcementPlayer.provide(frame);
        } else {
            didProvide = player.provide(frame);
        }

        if (didProvide) {
            getBuffer().flip();
        }
        return didProvide;
    }

}

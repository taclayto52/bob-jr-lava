package com.bob.jr;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.TrackEndEvent;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Stack;

public class TrackScheduler implements AudioLoadResultHandler {

    private final AudioPlayer player;
    private final Logger logger = LoggerFactory.getLogger(TrackScheduler.class.getName());
    private final Stack<AudioTrack> currentPlaylist = new Stack<>();

    public TrackScheduler(final AudioPlayer player) {
        this.player = player;
        player.addListener((event -> {
            if (event instanceof TrackEndEvent) {
                if (!currentPlaylist.isEmpty()) {
                    trackLoaded(currentPlaylist.pop());
                }
            }
            // else do nothing
        }));
    }

    public void clearPlaylist() {
        currentPlaylist.clear();
        player.stopTrack();
    }

    public List<AudioTrack> getAudioTracks() {
        return List.copyOf(currentPlaylist);
    }

    @Override
    public void trackLoaded(final AudioTrack track) {
        player.stopTrack();
        player.playTrack(track);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        // LavaPlayer found multiple AudioTracks from some playlist
        logger.info(String.format("playlist loaded: %s", playlist.getName()));
        player.stopTrack();
        currentPlaylist.clear();
        currentPlaylist.addAll(playlist.getTracks());
        Collections.reverse(currentPlaylist);
        player.playTrack(currentPlaylist.pop());
    }

    @Override
    public void noMatches() {
        // LavaPlayer did not find any audio to extract
        logger.info("found no matches to load item");
    }

    @Override
    public void loadFailed(final FriendlyException exception) {
        // LavaPlayer could not parse an audio source for some reason
        logger.error(String.format("failed to load item with exception: %s", exception.getMessage()));
    }

}

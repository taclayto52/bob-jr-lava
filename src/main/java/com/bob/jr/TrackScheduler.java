package com.bob.jr;

import com.bob.jr.utils.AnnouncementTrack;
import com.bob.jr.utils.AudioTrackCache;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.TrackMarker;
import com.sedmelluq.discord.lavaplayer.track.TrackMarkerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

public class TrackScheduler implements AudioLoadResultHandler {

    private final AudioPlayer player;
    private final AudioPlayer announcementPlayer;
    private final Logger logger = LoggerFactory.getLogger(TrackScheduler.class.getName());
    private final Stack<AudioTrack> currentPlaylist = new Stack<>();
    private final ConcurrentHashMap<String, AnnouncementTrack> announcementTracks = new ConcurrentHashMap<>();
    private final AudioTrackCache audioTrackCache;

    public TrackScheduler(final AudioPlayer player, final AudioPlayer announcementPlayer, final AudioTrackCache audioTrackCache) {
        this.player = player;
        this.announcementPlayer = announcementPlayer;
        this.audioTrackCache = audioTrackCache;
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
        handleTrackCache(track);
        synchronized (announcementTracks) {
            final var announcementTrackKey = announcementTracks.keySet().stream()
                    .filter(announcementKey -> announcementKey.contains(track.getIdentifier()))
                    .findAny();
            if (announcementTrackKey.isPresent()) {
                final AnnouncementTrack announcementTrack = announcementTracks.get(announcementTrackKey.get());

                // handle upcoming announcement
                track.setPosition(Math.round(announcementTrack.getStartTime() * 1000));
                final long setEndTime;
                if (announcementTrack.getEndTime() == 0) {
                    setEndTime = track.getDuration();
                } else {
                    setEndTime = Math.round(announcementTrack.getEndTime()) * 1000;
                }
                final TrackMarker trackMarker = new TrackMarker(setEndTime, (markerState) -> {
                    if (markerState == TrackMarkerHandler.MarkerState.REACHED) {
                        logger.debug("Marker has been reached");
                    } else if (markerState == TrackMarkerHandler.MarkerState.ENDED) {
                        logger.debug("Marker has ended");
                    } else {
                        logger.debug(String.format("reached unknown marker state: %s", markerState));
                    }
                    announcementTracks.remove(announcementTrack.getTrackUrl());
                    announcementPlayer.stopTrack();
                    setTrackPlayer();
                });
                track.setMarker(trackMarker);
                setAnnouncementPlayer();
                announcementPlayer.playTrack(track);
            } else {
                setTrackPlayer();
                player.stopTrack();
                player.playTrack(track);
            }
        }
    }

    private void handleTrackCache(final AudioTrack track) {
        if (audioTrackCache != null && !audioTrackCache.checkIfTrackIsPresent(track.getInfo().uri)) {
            audioTrackCache.addTrackToCache(track.getInfo().uri, track);
        }
    }

    @Override
    public void playlistLoaded(final AudioPlaylist playlist) {
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

    public synchronized void addToAnnouncementTrackQueue(final AnnouncementTrack announcementTrack) {
        final String announcementTrackKey = announcementTrack.getTrackUrl();
        announcementTracks.putIfAbsent(announcementTrackKey, announcementTrack);
    }

    public synchronized void setAnnouncementPlayer() {
        player.setPaused(true);
        announcementPlayer.setPaused(false);
    }

    public synchronized void setTrackPlayer() {
        announcementPlayer.setPaused(true);
        player.setPaused(false);
    }

}

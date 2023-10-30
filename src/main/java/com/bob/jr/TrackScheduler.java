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
import java.util.Optional;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

public class TrackScheduler implements AudioLoadResultHandler {

    private final AudioPlayer defaultTrackPlayer;
    private final AudioPlayer announcementTrackPlayer;
    private final Logger logger = LoggerFactory.getLogger(TrackScheduler.class.getName());
    private final Stack<AudioTrack> currentTrackPlaylist = new Stack<>();
    private final ConcurrentHashMap<String, AnnouncementTrack> announcementTracks = new ConcurrentHashMap<>();
    private final AudioTrackCache audioTrackCache;

    public TrackScheduler(final AudioPlayer defaultTrackPlayer, final AudioPlayer announcementTrackPlayer, final AudioTrackCache audioTrackCache) {
        this.defaultTrackPlayer = defaultTrackPlayer;
        this.announcementTrackPlayer = announcementTrackPlayer;
        this.audioTrackCache = audioTrackCache;
    }

    public void clearCurrentTrackPlaylist() {
        currentTrackPlaylist.clear();
        defaultTrackPlayer.stopTrack();
    }

    public List<AudioTrack> getAudioTracks() {
        return List.copyOf(currentTrackPlaylist);
    }

    @Override
    public void trackLoaded(final AudioTrack track) {
        updateTrackCache(track);

        synchronized (announcementTracks) {
            final var announcementTrackKey = getAnnouncementTrackKey(track);

            if (announcementTrackKey.isPresent()) {
                final AnnouncementTrack announcementTrack = announcementTracks.get(announcementTrackKey.get());

                final long setEndTime = setTrackStartAndEndTime(track, announcementTrack);

                final TrackMarker trackMarker = getTrackMarker(setEndTime, announcementTrack);

                track.setMarker(trackMarker);
                setAnnouncementTrackPlayerAsActive();
                announcementTrackPlayer.playTrack(track);
            } else {
                playTrackWithDefaultTrackPlayer(track);
            }
        }
    }

    private TrackMarker getTrackMarker(long setEndTime, AnnouncementTrack announcementTrack) {
        final TrackMarker trackMarker = new TrackMarker(setEndTime, (markerState) -> {
            if (markerState == TrackMarkerHandler.MarkerState.REACHED) {
                logger.debug("Marker has been reached");
            } else if (markerState == TrackMarkerHandler.MarkerState.ENDED) {
                logger.debug("Marker has ended");
            } else {
                logger.debug(String.format("reached unknown marker state: %s", markerState));
            }
            announcementTracks.remove(announcementTrack.getTrackUrl());
            announcementTrackPlayer.stopTrack();
            setDefaultTrackPlayerAsActive();
        });
        return trackMarker;
    }

    private static long setTrackStartAndEndTime(AudioTrack track, AnnouncementTrack announcementTrack) {
        track.setPosition(Math.round(announcementTrack.getStartTime() * 1000));
        final long setEndTime;
        if (announcementTrack.getEndTime() == 0) {
            setEndTime = track.getDuration();
        } else {
            setEndTime = Math.round(announcementTrack.getEndTime()) * 1000;
        }
        return setEndTime;
    }

    private Optional<String> getAnnouncementTrackKey(AudioTrack track) {
        final var announcementTrackKey = announcementTracks.keySet().stream()
                .filter(announcementKey -> announcementKey.contains(track.getIdentifier()))
                .findAny();
        return announcementTrackKey;
    }

    private void playTrackWithDefaultTrackPlayer(AudioTrack track) {
        setDefaultTrackPlayerAsActive();
        defaultTrackPlayer.stopTrack();
        defaultTrackPlayer.playTrack(track);
    }

    private void updateTrackCache(final AudioTrack track) {
        if (audioTrackCache != null && !audioTrackCache.checkIfTrackIsPresent(track.getInfo().uri)) {
            audioTrackCache.addTrackToCache(track.getInfo().uri, track);
        }
    }

    @Override
    public void playlistLoaded(final AudioPlaylist playlist) {
        // LavaPlayer found multiple AudioTracks from some playlist
        logger.info(String.format("playlist loaded: %s", playlist.getName()));

        clearCurrentTrackPlaylist();

        currentTrackPlaylist.addAll(playlist.getTracks());
        Collections.reverse(currentTrackPlaylist);

        defaultTrackPlayer.playTrack(currentTrackPlaylist.pop());
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

    public synchronized void setAnnouncementTrackPlayerAsActive() {
        defaultTrackPlayer.setPaused(true);
        announcementTrackPlayer.setPaused(false);
    }

    public synchronized void setDefaultTrackPlayerAsActive() {
        announcementTrackPlayer.setPaused(true);
        defaultTrackPlayer.setPaused(false);
    }

}

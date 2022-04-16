package com.bob.jr.utils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.time.Duration;

public class AudioTrackCache {

    private final Cache<String, AudioTrack> audioTrackCache;

    public AudioTrackCache() {
        audioTrackCache = CacheBuilder.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(30)).build();
    }

    public boolean checkIfTrackIsPresent(String key) {
        return audioTrackCache.getIfPresent(key) != null;
    }

    public AudioTrack getTrackFromCache(String key) {
        return audioTrackCache.getIfPresent(key).makeClone();
    }

    public void addTrackToCache(String key, AudioTrack audioTrack) {
        audioTrackCache.put(key, audioTrack);
    }

}

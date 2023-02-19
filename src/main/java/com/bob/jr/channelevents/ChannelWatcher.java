package com.bob.jr.channelevents;

import com.bob.jr.utils.AnnouncementTrack;
import com.bob.jr.utils.ServerResources;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Random;

public class ChannelWatcher {

    private final ServerResources serverResources;
    private final boolean testSoundClipLoad = true;

    public ChannelWatcher(final ServerResources serverResources) {
        this.serverResources = serverResources;
    }

    public Mono<Void> voiceStateUpdateEventHandler(final VoiceStateUpdateEvent voiceStateUpdateEvent) {
        final String contextString;
        final AnnouncementTrack.Actions contextAction;
        if (voiceStateUpdateEvent.isJoinEvent()) {
            contextString = "joined!";
            contextAction = AnnouncementTrack.Actions.JOINED;
        } else if (voiceStateUpdateEvent.isLeaveEvent()) {
            contextString = "left!";
            contextAction = AnnouncementTrack.Actions.LEFT;
        } else {
            // do nothing?
            return Mono.empty();
        }

        Mono<Void> monoVoid = null;
//        serverResources.getTrackScheduler().setAnnouncementPlayer();
        final var member = Mono.justOrEmpty(voiceStateUpdateEvent.getCurrent().getMember().block()).block();
        final var memberDisplayName = member.getDisplayName();
        if (member.isBot()) {
            return Mono.empty();
        }
        if (testSoundClipLoad && memberDisplayName.toLowerCase(Locale.ROOT).contains("vsepr")) {
            final String loadClipString = "soundFiles/Bimpson.webm";
            playAnnouncementTrack(loadClipString, -1, serverResources);
            monoVoid = Mono.empty();
        } else {
            final String userName = member.getDisplayName();
            final AnnouncementTrack announcementTrack = new AnnouncementTrack("synthString", member.getDisplayName(), contextAction);
            serverResources.getTrackScheduler().addToAnnouncementTrackQueue(announcementTrack);
            monoVoid = Mono.justOrEmpty(serverResources.getTextToSpeech().synthesizeTextMono(member, String.format("%s %s", userName, contextString)).block())
                    .doOnNext(fileLocation -> {
                        if (!serverResources.getAudioTrackCache().checkIfTrackIsPresent("synthString")) {
                            serverResources.getAudioPlayerManager().loadItem(fileLocation, serverResources.getTrackScheduler());
                        } else {
                            final var cachedAudioTrack = serverResources.getAudioTrackCache().getTrackFromCache("synthString");
                            serverResources.getTrackScheduler().trackLoaded(cachedAudioTrack);
                        }
                    })
                    .then();
        }
        return monoVoid;
    }

    // making function static for easier testing
    public static void playAnnouncementTrack(final String announcementUrl, final int startTime, final ServerResources serverResources) {
        final var trackStartTime = startTime == -1 ? new Random().nextInt(80) : startTime;
        final AnnouncementTrack announcementTrack = new AnnouncementTrack(announcementUrl, null, AnnouncementTrack.Actions.JOINED, trackStartTime, trackStartTime + 5);
        serverResources.getTrackScheduler().addToAnnouncementTrackQueue(announcementTrack);

        if (!serverResources.getAudioTrackCache().checkIfTrackIsPresent(announcementUrl)) {
            serverResources.getAudioPlayerManager().loadItem(announcementUrl, serverResources.getTrackScheduler());
        } else {
            // TODO need to implement way of loading a file directly from memory instead of a file
            final var cachedAudioTrack = serverResources.getAudioTrackCache().getTrackFromCache(announcementUrl);
            serverResources.getTrackScheduler().trackLoaded(cachedAudioTrack);
        }
    }
}

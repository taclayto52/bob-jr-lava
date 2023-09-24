package com.bob.jr.channelevents;

import com.bob.jr.BobJr;
import com.bob.jr.utils.AnnouncementTrack;
import com.bob.jr.utils.ServerResources;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Random;

import static com.bob.jr.utils.FluxUtils.logFluxError;

public class ChannelWatcher {

    private static final Logger logger = LoggerFactory.getLogger(BobJr.class);
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
        if (member.isBot()) {
            return Mono.empty();
        }
        if (voiceStateUpdateEvent.isJoinEvent() && testSoundClipLoad && member.getUsername().toLowerCase(Locale.ROOT).contains("vsepr")) {
            final String loadClipString = serverResources.handleFile("Bimpson.webm");
            playAnnouncementTrack(loadClipString, -1, serverResources);
            monoVoid = Mono.empty();
        } else {
            final String userName = member.getNickname().orElse(member.getDisplayName());
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
                    .doOnError(logFluxError(logger, "voiceStateUpdateEventHandler"))
                    .onErrorComplete()
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

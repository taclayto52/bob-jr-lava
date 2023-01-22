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

    public ChannelWatcher(ServerResources serverResources) {
        this.serverResources = serverResources;
    }

    public Mono<Void> voiceStateUpdateEventHandler(VoiceStateUpdateEvent voiceStateUpdateEvent) {
        String contextString;
        AnnouncementTrack.Actions contextAction;
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
        if (voiceStateUpdateEvent.isJoinEvent() && testSoundClipLoad && memberDisplayName.toLowerCase(Locale.ROOT).contains("vsepr")) {
            String loadClipString = "https://www.youtube.com/watch?v=LfwJJ6s66GE";

            final var randomStartTime = new Random().nextInt(80);
            AnnouncementTrack announcementTrack = new AnnouncementTrack(loadClipString, member.getDisplayName(), contextAction, randomStartTime, randomStartTime + 5);
            serverResources.getTrackScheduler().addToAnnouncementTrackQueue(announcementTrack);

            if (!serverResources.getAudioTrackCache().checkIfTrackIsPresent(loadClipString)) {
                serverResources.getAudioPlayerManager().loadItem(loadClipString, serverResources.getTrackScheduler());
            } else {
                // TODO need to implement way of loading a file directly from memory instead of a file
                final var cachedAudioTrack = serverResources.getAudioTrackCache().getTrackFromCache(loadClipString);
                serverResources.getTrackScheduler().trackLoaded(cachedAudioTrack);
            }
            monoVoid = Mono.empty();
        } else {
            final String userName = member.getDisplayName();
            AnnouncementTrack announcementTrack = new AnnouncementTrack("synthString", member.getDisplayName(), contextAction);
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
}

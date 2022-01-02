package com.bob.jr.channelevents;

import com.bob.jr.utils.AnnouncementTrack;
import com.bob.jr.utils.ServerResources;
import discord4j.core.event.domain.VoiceStateUpdateEvent;
import reactor.core.publisher.Mono;

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
        serverResources.getTrackScheduler().togglePlayers();
        if (testSoundClipLoad) {
            String loadClipString = "https://www.youtube.com/watch?v=2qEG25V2IpM";
            monoVoid = Mono.just(voiceStateUpdateEvent.getCurrent().getMember().block())
                    .filter(member -> !member.isBot())
                    .flatMap(member -> {
                        AnnouncementTrack announcementTrack = new AnnouncementTrack(loadClipString, member.getDisplayName(), contextAction, 1.0, 3.0);
                        serverResources.getTrackScheduler().addToAnnouncementTrackQueue(announcementTrack);
                        return Mono.just(loadClipString);
                    })
                    .doOnNext(loadUrl -> {
                        if (!serverResources.getAudioTrackCache().checkIfTrackIsPresent(loadUrl)) {
                            serverResources.getAudioPlayerManager().loadItem(loadUrl, serverResources.getTrackScheduler());
                        } else {
                            // TODO need to implement way of loading a file directly from memory instead of a file
                            final var cachedAudioTrack = serverResources.getAudioTrackCache().getTrackFromCache(loadUrl);
                            serverResources.getTrackScheduler().trackLoaded(cachedAudioTrack);
                        }


                    })
                    .then();
        } else {
            monoVoid = Mono.just(voiceStateUpdateEvent.getCurrent().getMember().block())
                    .filter(member -> !member.isBot())
                    .flatMap(member -> {
                        final String userName = member.getDisplayName();
                        AnnouncementTrack announcementTrack = new AnnouncementTrack("synthString", member.getDisplayName(), contextAction, 50.0, 7.0);
                        serverResources.getTrackScheduler().addToAnnouncementTrackQueue(announcementTrack);
                        return serverResources.getTextToSpeech().synthesizeTextMono(member, String.format("%s %s", userName, contextString));
                    })
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

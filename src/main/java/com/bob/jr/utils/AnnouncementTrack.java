package com.bob.jr.utils;

public class AnnouncementTrack {

    private final String trackUrl;
    private final String member;
    private final double startTime;
    private final double endTime;
    private final Actions action;

    public AnnouncementTrack(String trackUrl, String member, Actions action) {
        this(trackUrl, member, action, 0, 0);
    }

    // startTime and endTime in seconds
    public AnnouncementTrack(String trackUrl, String member, Actions action, double startTime, double endTime) {
        this.trackUrl = trackUrl;
        this.member = member;
        this.startTime = startTime;
        this.endTime = endTime;
        this.action = action;
    }

    public String getTrackUrl() {
        return trackUrl;
    }

    public String getMember() {
        return member;
    }

    public double getStartTime() {
        return startTime;
    }

    public double getEndTime() {
        return endTime;
    }

    public Actions getAction() {
        return action;
    }


    public static enum Actions {
        JOINED,
        LEFT
    }
}

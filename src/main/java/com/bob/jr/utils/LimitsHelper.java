package com.bob.jr.utils;

public class LimitsHelper {

    public static final int MESSAGE_LIMIT = 2000;
    public static final int PLAYLIST_RETURN_LIMIT = 10;

    public static class SpeakingRateLimits {
        public static Double upperLimit = 4.0;
        public static Double lowerLimit = 0.25;
    }

    public static class PitchLimits {
        public static Double upperLimit = 20.0;
        public static Double lowerLimit = -20.0;
    }

}

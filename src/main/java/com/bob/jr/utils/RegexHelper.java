package com.bob.jr.utils;

public class RegexHelper {
    private static final String DECIMAL_REGEX_BODY = "\\d+(\\.\\d+)?";
    public static final String POS_DECIMAL_REGEX = "^" + DECIMAL_REGEX_BODY + "$";
    public static final String POS_NEG_DECIMAL_REGEX = "^(-)?" + DECIMAL_REGEX_BODY + "$";
}

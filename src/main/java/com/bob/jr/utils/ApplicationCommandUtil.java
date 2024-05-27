package com.bob.jr.utils;

import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;

import java.util.NoSuchElementException;

public interface ApplicationCommandUtil {

    static String getApplicationOptionString(final ApplicationCommandInteractionEvent applicationCommandInteractionEvent,
                                             final String optionName) throws NoSuchElementException {
        return applicationCommandInteractionEvent
                .getInteraction().getCommandInteraction().orElseThrow()
                .getOption(optionName).orElseThrow()
                .getValue().orElseThrow()
                .asString();
    }

    static boolean getApplicationOptionBoolean(final ApplicationCommandInteractionEvent applicationCommandInteractionEvent,
                                               final String optionName) throws NoSuchElementException {
        return applicationCommandInteractionEvent
                .getInteraction().getCommandInteraction().orElseThrow()
                .getOption(optionName).orElseThrow()
                .getValue().orElseThrow()
                .asBoolean();
    }

    static long getApplicationOptionLong(final ApplicationCommandInteractionEvent applicationCommandInteractionEvent,
                                         final String optionName) throws NoSuchElementException {
        return applicationCommandInteractionEvent
                .getInteraction().getCommandInteraction().orElseThrow()
                .getOption(optionName).orElseThrow()
                .getValue().orElseThrow()
                .asLong();
    }

}

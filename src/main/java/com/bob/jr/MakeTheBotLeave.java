package com.bob.jr;

import com.bob.jr.interfaces.Command;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.VoiceState;
import reactor.core.publisher.Mono;

public class MakeTheBotLeave implements Command {
    private final GatewayDiscordClient client;

    public MakeTheBotLeave (GatewayDiscordClient client) {
        this.client = client;
    }

    @Override
    public Mono<Void> execute(Intent intent) {
        return Mono.justOrEmpty(intent.getMessageCreateEvent())
                .flatMap(event -> {
                    Snowflake guildSnow = event.getGuild().block().getId();
                    VoiceState voiceState = client.getMemberById(guildSnow, client.getSelfId())
                            .block()
                            .getVoiceState()
                            .block();
                    return voiceState.getChannel().block().getVoiceConnection().block().disconnect();
                })
                .then();
    }
}

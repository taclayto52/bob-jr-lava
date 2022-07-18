package com.bob.jr;

import com.bob.jr.commands.CommandStore;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.GatewayDiscordClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ApplicationCommandRegTools {

    private static final Logger logger = LoggerFactory.getLogger(CommandStore.class);
    GatewayDiscordClient gatewayDiscordClient;

    public ApplicationCommandRegTools(String[] args) {
        // setup tool stuff
        gatewayDiscordClient = createGatewayDiscordClient(args[0]);
        CommandStore commandStore = new CommandStore(gatewayDiscordClient);

        if (args.length == 1) {
            throw new IllegalArgumentException("More args please");
        }
        String command = args[1].toLowerCase(Locale.ROOT);

        if (command.equals("delete")) {
            if (args.length > 2) {
                // just delete provided commands
                List<String> listOfCommands = new ArrayList<>();
                for (int i = 1; i < args.length; i++) {
                    listOfCommands.add(args[i]);
                }
                commandStore.deregisterCommand(listOfCommands);
            } else {
                // delete all
                commandStore.deregisterCommand(List.of());
            }
        }

        if (command.equals("list")) {
            final var applicationCommands = commandStore.listApplicationCommands();
            StringBuilder printout = new StringBuilder();
            printout.append(String.format("%nApplication command name | description%n"));
            applicationCommands.block().forEach(applicationCommandData -> {
                printout.append(String.format("%s | %s%n", applicationCommandData.name(), applicationCommandData.description()));
            });

            logger.info(printout.toString());
        }
    }

    public static void main(String[] args) {
        new ApplicationCommandRegTools(args);
    }

    private GatewayDiscordClient createGatewayDiscordClient(String token) {
        // setup client
        return DiscordClientBuilder.create(token).build()
                .login()
                .block();
    }

}

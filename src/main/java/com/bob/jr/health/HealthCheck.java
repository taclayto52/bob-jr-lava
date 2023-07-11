package com.bob.jr.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static com.bob.jr.utils.RegexHelper.IP_REGEX;

public class HealthCheck implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheck.class);
    final static Predicate<String> ipMatchPredicate = Pattern.compile(IP_REGEX).asMatchPredicate();
    final static ByteBuffer BYTE_RESPONSE = ByteBuffer.wrap("OK".getBytes(StandardCharsets.US_ASCII));
    final AsynchronousServerSocketChannel serverSocket;
    boolean running;

    public HealthCheck(final int port) throws IOException {
        final var networkInterface = NetworkInterface.getByName("eth0");

        final var internalHostAddress = networkInterface.getInterfaceAddresses().stream()
                .map(interfaceAddress -> interfaceAddress.getAddress().getHostAddress())
                .filter(ipMatchPredicate)
                .findFirst()
                .orElse("localhost");
        logger.info("Setting internal host address: {}", internalHostAddress);
        
        final var inetSocketAddress = new InetSocketAddress(internalHostAddress, port);
        serverSocket = AsynchronousServerSocketChannel.open().bind(inetSocketAddress);
        running = true;
    }

    @Override
    public void run() {
        while (running) {
            try (final var openSocket = serverSocket.accept().get()) {
                openSocket.write(BYTE_RESPONSE).get();
            } catch (ExecutionException | InterruptedException | IOException e) {
                throw new RuntimeException(e);
            } finally {
                BYTE_RESPONSE.clear();
            }
        }
    }
}

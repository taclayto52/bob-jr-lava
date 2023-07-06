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

public class HealthCheck implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(HealthCheck.class);

    final static ByteBuffer BYTE_RESPONSE = ByteBuffer.wrap("OK".getBytes(StandardCharsets.US_ASCII));
    final AsynchronousServerSocketChannel serverSocket;
    boolean running;

    public HealthCheck(final int port) throws IOException {
        final var networkInterfaceIterator = NetworkInterface.getNetworkInterfaces().asIterator();
        while (networkInterfaceIterator.hasNext()) {
            final var networkInterface = networkInterfaceIterator.next();
            logger.info("Network interface - name: {}", networkInterface.getDisplayName());
            final var inetAddresses = networkInterface.inetAddresses();
            inetAddresses.forEach(inetAddress -> logger.info("address: {}; hostaddress: {};", inetAddress.getHostName(), inetAddress.getHostAddress()));
        }
        final var inetSocketAddress = new InetSocketAddress("localhost", port);
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

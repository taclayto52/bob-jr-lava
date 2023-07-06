package com.bob.jr.health;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

public class HealthCheck implements Runnable {

    final static ByteBuffer BYTE_RESPONSE = ByteBuffer.wrap("OK".getBytes(StandardCharsets.US_ASCII));
    final AsynchronousServerSocketChannel serverSocket;
    boolean running;

    public HealthCheck(final int port) throws IOException {
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

package com.deepfind.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import org.springframework.context.ConfigurableApplicationContext;

/** An inherited pipe, not a network shutdown endpoint. EOF also handles a terminated parent. */
public final class DesktopParentConnection {
    private boolean disconnected;
    private ConfigurableApplicationContext context;

    public void watch(InputStream input) {
        Thread.ofPlatform().daemon().name("deepfind-desktop-parent").start(() -> {
            try {
                input.read();
            } catch (IOException ignored) {
                // A broken parent pipe is equivalent to a close request.
            }
            disconnect();
        });
    }

    public synchronized void ready(ConfigurableApplicationContext application, int port, PrintStream output) {
        context = application;
        if (disconnected) {
            context.close();
            return;
        }
        if (port < 1 || port > 65535) {
            context.close();
            throw new IllegalArgumentException("Invalid desktop listener port");
        }
        output.println("DEEPFIND_READY " + port);
        output.flush();
    }

    synchronized void disconnect() {
        disconnected = true;
        if (context != null) {
            context.close();
        }
    }
}

package com.deepfind.diagnostics;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public final class ApplicationLifecycleLogger {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApplicationLifecycleLogger.class);

    @EventListener(ApplicationReadyEvent.class)
    public void started() {
        LOGGER.info("event=application_started networkScope=loopback");
    }

    @PreDestroy
    public void stopped() {
        LOGGER.info("event=application_stopped");
    }
}

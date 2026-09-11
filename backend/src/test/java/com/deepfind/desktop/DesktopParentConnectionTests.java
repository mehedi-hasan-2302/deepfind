package com.deepfind.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class DesktopParentConnectionTests {
    @Test
    void reportsOnlyThePortAndClosesOnParentEof() {
        var context = mock(ConfigurableApplicationContext.class);
        var bytes = new ByteArrayOutputStream();
        var connection = new DesktopParentConnection();
        connection.ready(context, 12345, new PrintStream(bytes));
        assertThat(bytes.toString()).isEqualTo("DEEPFIND_READY 12345" + System.lineSeparator());
        connection.watch(new ByteArrayInputStream(new byte[0]));
        verify(context, timeout(2000)).close();
    }

    @Test
    void parentExitDuringStartupClosesWithoutAdvertisingReadiness() {
        var context = mock(ConfigurableApplicationContext.class);
        var bytes = new ByteArrayOutputStream();
        var connection = new DesktopParentConnection();
        connection.disconnect();
        connection.ready(context, 12345, new PrintStream(bytes));
        verify(context).close();
        assertThat(bytes.toString()).isEmpty();
    }
}

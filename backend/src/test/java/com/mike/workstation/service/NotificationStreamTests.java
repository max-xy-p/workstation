package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import com.mike.workstation.config.NotificationProperties;
import com.mike.workstation.exception.NotificationStreamLimitException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import org.junit.jupiter.api.Test;
import java.time.Instant;

class NotificationStreamTests {
    @Test
    void connectionLimitsAndTokenLifetimeBoundTheHub() throws Exception {
        var hub = new NotificationStreamHub(new NotificationProperties(true, 2, 1, 90));
        try {
            var first = hub.open("one", Instant.now().plusSeconds(120));
            assertThat(first.getTimeout()).isEqualTo(55000L);
            assertThatThrownBy(() -> hub.open("one", Instant.now().plusSeconds(120))).isInstanceOf(NotificationStreamLimitException.class);
            var second = hub.open("two", Instant.now().plusSeconds(2));
            assertThat(second.getTimeout()).isBetween(1L, 2000L);
            assertThatThrownBy(() -> hub.open("three", Instant.now().plusSeconds(120))).isInstanceOf(NotificationStreamLimitException.class);
            assertThatThrownBy(() -> hub.open("expired", Instant.now().minusSeconds(1))).isInstanceOf(WorkspaceAccessDeniedException.class);
            hub.changed("one");
            hub.changed("unknown");
            Thread.sleep(100);
        } finally {
            hub.shutdown();
        }
    }

    @Test
    void propertiesRejectUnboundedConnectionsRetentionAndInvalidInstanceIdentity() {
        assertThatThrownBy(() -> new NotificationProperties(true, 0, 1, 90)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationProperties(true, 10, 11, 90)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationProperties(true, 10, 1, 366)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NotificationProperties(true, 10, 1, 90, "bad/instance")).isInstanceOf(IllegalArgumentException.class);
    }
}

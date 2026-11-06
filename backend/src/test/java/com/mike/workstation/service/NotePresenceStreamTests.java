package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import com.mike.workstation.config.NotePresenceProperties;
import com.mike.workstation.exception.NotePresenceException;
import com.mike.workstation.exception.WorkspaceAccessDeniedException;
import org.junit.jupiter.api.Test;
import java.time.Instant;

class NotePresenceStreamTests {
    @Test
    void connectionsAcrossDifferentNotesSharePerUserLimitAndRespectTokenExpiry() {
        var hub = new NotePresenceStreamHub(new NotePresenceProperties(true));
        try {
            for (int index = 1; index <= 5; index++) {
                var stream = hub.open(NotePresenceStreamHub.topic(1, index), "same-user", Instant.now().plusSeconds(90));
                assertThat(stream.getTimeout()).isEqualTo(55000L);
            }
            assertThatThrownBy(() -> hub.open("2:1", "same-user", Instant.now().plusSeconds(90)))
                    .isInstanceOf(NotePresenceException.class);
            assertThatThrownBy(() -> hub.open("1:1", "expired", Instant.now().minusSeconds(1)))
                    .isInstanceOf(WorkspaceAccessDeniedException.class);
            assertThat(hub.open("1:1", "other-user", Instant.now().plusSeconds(1)).getTimeout()).isBetween(1L, 1000L);
            hub.changed("1:1");
            hub.changed("missing");
        } finally {
            hub.shutdown();
        }
    }
}

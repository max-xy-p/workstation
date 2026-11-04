package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.config.NotificationProperties;
import com.mike.workstation.domain.NotificationEvent;
import com.mike.workstation.domain.NotificationResourceType;
import com.mike.workstation.domain.NotificationRefresh;
import com.mike.workstation.mapper.NotificationEventMapper;
import com.mike.workstation.mapper.NotificationMapper;
import com.mike.workstation.mapper.NotificationSignalMapper;
import com.mike.workstation.mapper.WorkspaceMapper;
import com.mike.workstation.messaging.NotificationMessagePublisher;
import com.mike.workstation.messaging.NotificationMessageConsumer;
import com.mike.workstation.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

class NotificationWriterTests {
    private final NotificationCodec codec = new NotificationCodec(new ObjectMapper());
    private final NotificationEventMapper events = mock(NotificationEventMapper.class);
    private final NotificationMapper notifications = mock(NotificationMapper.class);
    private final WorkspaceMapper workspaces = mock(WorkspaceMapper.class);
    private final NotificationProperties enabled = new NotificationProperties(true, 500, 5, 90);
    private final NotificationWriter writer = new NotificationWriter(events, notifications, workspaces, codec, enabled);

    @Test
    void directWriteNormalizesRecipientsAndSavesNotificationBeforeReturning() {
        when(workspaces.lockWorkspace(9L)).thenReturn(9L);
        when(events.insert(any())).thenReturn(1);
        when(notifications.eligibleRecipients(eq(9L), any())).thenReturn(List.of("user-a"));
        when(notifications.lockInbox("user-a")).thenReturn(4L);
        writer.writeRequired(command());
        verify(notifications).insert(argThat(n -> n.getSequence() == 5 && n.getRecipient().equals("user-a")));
        verify(notifications).advance("user-a");
        verify(events).insert(argThat(e -> e.getFingerprint().length() == 64));
    }

    @Test
    void duplicateMetadataPreventsRecreationAndRejectsChangedPayload() {
        var command = command();
        when(workspaces.lockWorkspace(9L)).thenReturn(9L);
        var existing = new NotificationEvent();
        existing.setFingerprint(codec.fingerprint(codec.encode(codec.normalize(command, Instant.now()))));
        when(events.lock(command.eventId())).thenReturn(existing);
        writer.writeRequired(command);
        verifyNoInteractions(notifications);
        existing.setFingerprint("different");
        assertThatThrownBy(() -> writer.writeRequired(command)).isInstanceOf(IllegalArgumentException.class);
        when(workspaces.lockWorkspace(9L)).thenReturn(null);
        assertThatThrownBy(() -> writer.writeRequired(command)).isInstanceOf(NotFoundException.class);
        new NotificationWriter(events, notifications, workspaces, codec,
                new NotificationProperties(false, 500, 5, 90)).writeRequired(null);
    }

    @Test
    void codecRejectsInvalidTargetsRecipientsAndIdentifiers() {
        assertThatThrownBy(() -> codec.normalize(null, Instant.now())).isInstanceOf(IllegalArgumentException.class);
        var c = command();
        assertThatThrownBy(() -> codec.normalize(new NotificationCommand("bad", 9L, "TEST", "标题", "",
                NotificationResourceType.WORKSPACE, null, Instant.now(), List.of("user")), Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.normalize(new NotificationCommand(c.eventId(), 9L, "TEST", "标题", "",
                NotificationResourceType.PROJECT, null, Instant.now(), List.of("user")), Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.normalize(new NotificationCommand(c.eventId(), 9L, "TEST", "标题", "",
                NotificationResourceType.WORKSPACE, 1L, Instant.now(), List.of("user")), Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.normalize(new NotificationCommand(c.eventId(), 9L, "TEST", "标题", "",
                NotificationResourceType.WORKSPACE, null, Instant.now(), List.of("bad\nsubject")), Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failedSignalPublishRemainsPendingAndConfirmedPublishUsesSnapshotRevision() throws Exception {
        var signals = mock(NotificationSignalMapper.class);
        var publisher = mock(NotificationMessagePublisher.class);
        var relay = new NotificationSignalRelay(enabled, signals, publisher, notifications, events);
        when(signals.due(any())).thenReturn(List.of("user"));
        when(signals.claim(eq("user"), anyString(), any(), any())).thenReturn(1);
        var snapshot = new NotificationRefresh("user", 7);
        when(signals.snapshot("user")).thenReturn(snapshot);
        doThrow(new IllegalStateException("模拟 MQ 不可用")).doNothing().when(publisher).publish(snapshot);
        relay.relay();
        verify(signals, never()).published(any(), any(), anyLong(), any());
        relay.relay();
        verify(signals).published(eq("user"), anyString(), eq(7L), any());
        relay.maintenance();
        verify(notifications).purge(any());
        verify(events).purge(any());
    }

    @Test
    void refreshConsumerIgnoresMalformedPayloadAndRetriesLocalFailures() {
        var streams = mock(NotificationStreamHub.class);
        var json = new ObjectMapper();
        var consumer = new NotificationMessageConsumer(streams, json);
        consumer.receive(message("not-json".getBytes(StandardCharsets.UTF_8)));
        consumer.receive(message(new byte[1025]));
        consumer.receive(message(json.writeValueAsBytes(new NotificationRefresh("user", 0))));
        verifyNoInteractions(streams);
        var valid = message(json.writeValueAsBytes(new NotificationRefresh("user", 2)));
        consumer.receive(valid);
        verify(streams).changed("user");
        doThrow(new IllegalStateException("不得泄漏")).when(streams).changed("user");
        assertThatThrownBy(() -> consumer.receive(valid)).isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("通知刷新提醒暂未处理，稍后重投").hasCause(null);
    }

    private Message message(byte[] body) {
        return new Message(body, new MessageProperties());
    }

    private NotificationCommand command() {
        return new NotificationCommand(UUID.randomUUID().toString(), 9L, "TEST_EVENT", "标题", "正文",
                NotificationResourceType.WORKSPACE, null, Instant.now(), List.of("user-b", "user-a", "user-a"));
    }
}

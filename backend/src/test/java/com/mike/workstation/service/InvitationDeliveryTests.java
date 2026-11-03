package com.mike.workstation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mike.workstation.config.InvitationProperties;
import com.mike.workstation.domain.WorkspaceInvitation;
import com.mike.workstation.mapper.InvitationMapper;
import com.mike.workstation.messaging.InvitationPublisher;
import com.mike.workstation.messaging.InvitationConsumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.UUID;

class InvitationDeliveryTests {
    private final InvitationMapper mapper = mock(InvitationMapper.class);
    private final InvitationMailClient mail = mock(InvitationMailClient.class);
    private final InvitationProperties properties = new InvitationProperties(true, "https://work.example/",
            Base64.getEncoder().encodeToString(new byte[32]), "http://mail:8082", "x".repeat(32));
    private final InvitationTokenService tokens = new InvitationTokenService(properties);
    private final InvitationDeliveryService delivery = new InvitationDeliveryService(mapper, tokens, mail, properties);
    private final String id = UUID.randomUUID().toString();

    @Test
    void ambiguousSubmissionRetriesIdenticalBodyAndStableKey() throws Exception {
        var invitation = invitation();
        when(mapper.findDelivery(id)).thenReturn(invitation);
        doThrow(new IllegalStateException("模拟响应丢失")).doNothing().when(mail).enqueue(any());
        assertThatThrownBy(() -> delivery.deliver(id)).isInstanceOf(IllegalStateException.class);
        verify(mapper, never()).completed(any(), any());
        delivery.deliver(id);
        var requests = ArgumentCaptor.forClass(InvitationMailClient.MailRequest.class);
        verify(mail, times(2)).enqueue(requests.capture());
        assertThat(requests.getAllValues().get(0)).isEqualTo(requests.getAllValues().get(1));
        assertThat(requests.getValue().requestKey()).isEqualTo("workspace-invitation/" + invitation.getId());
        assertThat(requests.getValue().text()).contains("/invitations#").doesNotContain("?token=");
        verify(mapper).completed(id, "ACCEPTED");
    }

    @Test
    void terminalRevokedExpiredAndRotatedCredentialsNeverSend() throws Exception {
        when(mapper.findDelivery(id)).thenReturn(null);
        delivery.deliver(id);
        var invitation = invitation();
        when(mapper.findDelivery(id)).thenReturn(invitation);
        invitation.setMailStatus("ACCEPTED");
        delivery.deliver(id);
        invitation.setMailStatus("PUBLISHED");
        invitation.setStatus("REVOKED");
        delivery.deliver(id);
        verify(mapper).completed(id, "CANCELLED");
        invitation.setStatus("PENDING");
        invitation.setExpiresAt(LocalDateTime.now(ZoneId.systemDefault()).minusSeconds(1));
        delivery.deliver(id);
        verify(mapper).completed(id, "EXPIRED");
        invitation.setExpiresAt(LocalDateTime.now(ZoneId.systemDefault()).plusHours(1));
        invitation.setTokenHash("changed");
        delivery.deliver(id);
        verifyNoInteractions(mail);
    }

    @Test
    void outboxRetriesFailureAndUsesLeaseConditionalCompletion() throws Exception {
        var publisher = mock(InvitationPublisher.class);
        var relay = new InvitationOutboxRelay(mapper, publisher, properties);
        var now = LocalDateTime.now(ZoneId.systemDefault());
        when(mapper.due(any())).thenReturn(java.util.List.of(id));
        when(mapper.claim(eq(id), anyString(), any(), any())).thenReturn(1);
        var invitation = invitation();
        when(mapper.findDelivery(id)).thenReturn(invitation);
        doThrow(new IllegalStateException("模拟 Broker 不可用")).doNothing().when(publisher).publish(id);
        relay.relay();
        verify(mapper).published(eq(id), anyString(), eq("PENDING"), any());
        relay.relayOne(id, now);
        verify(mapper).published(eq(id), anyString(), eq("PUBLISHED"), any());
        invitation.setStatus("REVOKED");
        relay.relayOne(id, now);
        verify(mapper).published(eq(id), anyString(), eq("CANCELLED"), any());
        invitation.setExpiresAt(now.minusSeconds(1));
        relay.relayOne(id, now);
        verify(mapper).published(eq(id), anyString(), eq("EXPIRED"), any());
        when(mapper.claim(eq(id), anyString(), any(), any())).thenReturn(0);
        relay.relayOne(id, now);
        verify(publisher, times(2)).publish(id);
    }

    @Test
    void consumerRejectsTransientFailuresAndIgnoresInvalidReferences() throws Exception {
        var service = mock(InvitationDeliveryService.class);
        var consumer = new InvitationConsumer(service);
        consumer.receive(message("secret-content"));
        consumer.receive(message("z".repeat(36)));
        verifyNoInteractions(service);
        consumer.receive(message(id));
        verify(service).deliver(id);
        doThrow(new IllegalStateException("不得泄漏")).when(service).deliver(id);
        assertThatThrownBy(() -> consumer.receive(message(id)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class)
                .hasMessage("邀请暂未完成投递").hasCause(null);
    }

    @Test
    void configurationRejectsMissingKeysAndUnsafeUrls() {
        assertThat(new InvitationProperties(false, "", "", "", "").enabled()).isFalse();
        assertThatThrownBy(() -> new InvitationProperties(true, "https://work.example", "", "http://mail", "x".repeat(32)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvitationProperties(true, "https://user:pass@work.example",
                properties.signingKey(), "http://mail", properties.mailToken())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvitationProperties(true, "https://work.example?redirect=bad",
                properties.signingKey(), "http://mail", properties.mailToken())).isInstanceOf(IllegalArgumentException.class);
    }

    private Message message(String body) {
        return new Message(body.getBytes(StandardCharsets.US_ASCII), new MessageProperties());
    }

    private WorkspaceInvitation invitation() {
        var i = new WorkspaceInvitation();
        i.setId(UUID.randomUUID().toString());
        i.setWorkspaceName("研发");
        i.setEmail("guest@example.com");
        i.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()).withNano(0));
        i.setExpiresAt(i.getCreatedAt().plusHours(23));
        i.setStatus("PENDING");
        i.setMailStatus("PENDING");
        i.setTokenHash(tokens.hash(tokens.token(i.getId())));
        return i;
    }
}

package com.mike.workstation.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.mike.workstation.config.IntegrationProperties;
import com.mike.workstation.domain.*;
import com.mike.workstation.dto.*;
import com.mike.workstation.exception.*;
import com.mike.workstation.integration.*;
import com.mike.workstation.mapper.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class GithubWebhookServiceTests {
    private final GithubWebhookMapper hooks=mock(GithubWebhookMapper.class);
    private final IntegrationMapper integrations=mock(IntegrationMapper.class);
    private final IssueSourceMapper sources=mock(IssueSourceMapper.class);
    private final IntegrationStateService state=mock(IntegrationStateService.class);
    private final WorkspaceAuthorizationService auth=mock(WorkspaceAuthorizationService.class);
    private final CredentialCipher cipher=mock(CredentialCipher.class);
    private final AuditService audit=mock(AuditService.class);
    private final IntegrationProperties props=new IntegrationProperties(true,"v1",Map.of(),List.of(),15,10,100,180,30);
    private GithubWebhookService service;
    private GithubWebhook hook;
    private IntegrationConnection connection;
    private IssueSource source;
    private final String secret="random-test-value-32-characters-long";
    @BeforeEach void setup() {
        service=new GithubWebhookService(hooks,integrations,sources,state,auth,cipher,props,audit,new ObjectMapper());
        hook=new GithubWebhook();hook.setId("endpoint");hook.setWorkspaceId(9L);hook.setConnectionId(2L);hook.setEnabled(true);hook.setVersion(1);hook.setSecret("encrypted");hook.setKeyVersion("v1");
        connection=new IntegrationConnection();connection.setWorkspaceId(9L);connection.setId(2L);connection.setProvider("GITHUB");connection.setEnabled(true);
        when(integrations.connection(9L,2L)).thenReturn(connection);when(integrations.lockConnection(9L,2L)).thenReturn(connection);
        when(state.canManage(connection)).thenReturn(true);when(hooks.endpoint("endpoint")).thenReturn(hook);when(hooks.lock(9L,2L)).thenReturn(hook);
        when(cipher.decrypt(anyString(),anyString(),anyString())).thenReturn(secret);when(cipher.encrypt(anyString(),anyString())).thenReturn("encrypted-new");
        source=new IssueSource();source.setId(4L);source.setIssueNumber(42L);source.setLinkToken("generation");source.setTrackingStatus("ACTIVE");
        when(sources.external(9L,2L,"1000","100")).thenReturn(source);
    }
    private byte[] body() { return "{\"action\":\"closed\",\"repository\":{\"id\":1000},\"issue\":{\"id\":100,\"number\":42,\"title\":\"中文来源\"}}".getBytes(StandardCharsets.UTF_8); }
    private String sign(byte[] body) throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return "sha256="+HexFormat.of().formatHex(mac.doFinal(body));
    }
    @Test void validUnicodeDeliveryPersistsOnlyMetadataAndCapturesGeneration() throws Exception {
        service.receive("endpoint","delivery-1","issues",sign(body()),body());var capture=ArgumentCaptor.forClass(GithubWebhookDelivery.class);verify(hooks).insert(capture.capture());
        assertThat(capture.getValue().getStatus()).isEqualTo("RECEIVED");assertThat(capture.getValue().getSourceId()).isEqualTo(4L);assertThat(capture.getValue().getLinkToken()).isEqualTo("generation");assertThat(capture.getValue().getBodyHash()).isEqualTo(WebhookSignature.hash(body()));
    }
    @Test void invalidSignatureNeverPersistsAndDisabledHookCannotReceive() {
        assertThatThrownBy(()->service.receive("endpoint","delivery","issues","sha256="+"0".repeat(64),body())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);verify(hooks,never()).insert(any());
        hook.setEnabled(false);assertThatThrownBy(()->service.receive("endpoint","delivery","issues",null,body())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void duplicateIsIdempotentButChangedContentConflicts() throws Exception {
        var existing=new GithubWebhookDelivery();existing.setBodyHash(WebhookSignature.hash(body()));existing.setEventType("issues");when(hooks.duplicate("endpoint","delivery")).thenReturn(existing);
        service.receive("endpoint","delivery","issues",sign(body()),body());verify(hooks,never()).insert(any());
        existing.setBodyHash("different");assertThatThrownBy(()->service.receive("endpoint","delivery","issues",sign(body()),body())).isInstanceOf(IntegrationConflictException.class);
    }
    @Test void pingAndUnlinkedIssueArePersistedAsIgnored() throws Exception {
        byte[] ping="{}".getBytes(StandardCharsets.UTF_8);service.receive("endpoint","ping","ping",sign(ping),ping);
        when(sources.external(anyLong(),anyLong(),anyString(),anyString())).thenReturn(null);service.receive("endpoint","unlinked","issues",sign(body()),body());
        var capture=ArgumentCaptor.forClass(GithubWebhookDelivery.class);verify(hooks,times(2)).insert(capture.capture());assertThat(capture.getAllValues()).allSatisfy(d->assertThat(d.getStatus()).isEqualTo("IGNORED"));
    }
    @Test void invalidIdentityJsonAndHeadersAreRejectedAfterSignature() throws Exception {
        for(String raw:List.of("[]","bad-json","{}")) { byte[] bytes=raw.getBytes(StandardCharsets.UTF_8);assertThatThrownBy(()->service.receive("endpoint","invalid","issues",sign(bytes),bytes)).isInstanceOf(BusinessException.class); }
        assertThatThrownBy(()->service.receive("endpoint","bad key","issues",sign(body()),body())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.receive("endpoint","delivery","UPPER",sign(body()),body())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->service.receive("endpoint","delivery","issues",null,new byte[1_000_001])).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void noEndpointAndRevokedOwnerFailClosed() throws Exception {
        assertThatThrownBy(()->service.receive("missing","delivery","issues",sign(body()),body())).isInstanceOf(NotFoundException.class);
        when(state.canManage(connection)).thenReturn(false);assertThatThrownBy(()->service.receive("endpoint","delivery","issues",sign(body()),body())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void configCreatesEncryptedSecretAndNeverReturnsIt() {
        when(hooks.lock(9L,2L)).thenReturn(null);var result=service.configure(9L,2L,new GithubWebhookRequest(true,0,secret));assertThat(result.configured()).isTrue();assertThat(result.endpointPath()).startsWith("/api/hooks/github/");assertThat(result.toString()).doesNotContain(secret);verify(cipher).encrypt(eq(secret),startsWith("webhook:9:2:"));
    }
    @Test void configVersionAndSecretLengthAreChecked() {
        assertThatThrownBy(()->service.configure(9L,2L,new GithubWebhookRequest(true,0,null))).isInstanceOf(IntegrationConflictException.class);
        assertThatThrownBy(()->service.configure(9L,2L,new GithubWebhookRequest(true,1,"short"))).isInstanceOf(BusinessException.class);
        service.configure(9L,2L,new GithubWebhookRequest(false,1,null));verify(hooks).cancelRuns(9L,2L);verify(hooks).cancelDeliveries(9L,2L);assertThat(hook.isEnabled()).isFalse();
    }
    @Test void managementRejectsOtherPlatformsAndSupportsEmptyRead() {
        when(hooks.get(9L,2L)).thenReturn(null);assertThat(service.get(9L,2L).configured()).isFalse();connection.setProvider("GITLAB");assertThatThrownBy(()->service.get(9L,2L)).isInstanceOf(BusinessException.class);
    }
    @Test void manualRetryRechecksGenerationAndOnlyAllowsFailedWork() {
        GithubWebhookDelivery d=new GithubWebhookDelivery();d.setId(6L);d.setSourceId(4L);d.setLinkToken("generation");d.setStatus("FAILED");d.setAttempts(3);when(hooks.delivery(9L,2L,6L)).thenReturn(d);when(sources.tracked(9L,4L)).thenReturn(source);
        assertThat(service.retry(9L,2L,6L).status()).isEqualTo("RECEIVED");assertThat(d.getAttempts()).isZero();
        assertThatThrownBy(()->service.retry(9L,2L,6L)).isInstanceOf(BusinessException.class);
        d.setStatus("CANCELLED");source.setLinkToken("new-generation");assertThatThrownBy(()->service.retry(9L,2L,6L)).isInstanceOf(IntegrationConflictException.class);
    }
    @Test void listUsesWorkspaceAndOffsetPagination() {
        when(hooks.list(9L,2L,20,20)).thenReturn(List.of());assertThat(service.deliveries(9L,2L,1,20).page()).isEqualTo(1);
        assertThatThrownBy(()->service.deliveries(9L,2L,0,101)).isInstanceOf(IllegalArgumentException.class);
    }
}

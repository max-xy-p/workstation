package com.mike.workstation.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.mike.workstation.domain.*;
import java.net.http.*;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RemoteApiTests {
    private final HttpClient client=mock(HttpClient.class);
    private final RemoteApi api=new RemoteApi(CredentialCipherTests.properties(),new ObjectMapper(),client);
    private IntegrationConnection connection(String provider,String base) {
        IntegrationConnection c=new IntegrationConnection();c.setProvider(provider);c.setBaseUrl(base);return c;
    }
    @Test void instanceValidationRejectsUnapprovedHostsCredentialsAndRedirectOrigins() {
        assertThat(api.normalize(IntegrationProvider.GITHUB,"https://github.com/")).isEqualTo("https://github.com");
        assertThat(api.normalize(IntegrationProvider.GITLAB,"https://GITLAB.COM/")).isEqualTo("https://gitlab.com");
        assertThat(api.normalize(IntegrationProvider.GITLAB,"https://gitlab.example.test/team/")).endsWith("/team");
        for(String bad:List.of("http://gitlab.com","https://user:secret@gitlab.com","https://gitlab.com?url=x","https://gitlab.com#x","https://unknown.test","https://gitlab.com/../x","https://gitlab.com/%2e%2e")) {
            assertThatThrownBy(()->api.normalize(IntegrationProvider.GITLAB,bad)).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(()->api.normalize(IntegrationProvider.GITHUB,"https://evil.test")).hasMessageContaining("GitHub");
    }
    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status,String body) {
        HttpResponse<String> r=mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        lenient().when(r.body()).thenReturn(body);
        lenient().when(r.headers()).thenReturn(HttpHeaders.of(Map.of(),(a,b)->true));
        return r;
    }
    @Test void requestsArePinnedToApprovedApiAndNeverExposeTokenInErrors() throws Exception {
        doReturn(response(200,"{\"ok\":true}")).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var c=connection("GITHUB","https://github.com");
        assertThat(api.get(c,"private-token","/user").path("ok").asBoolean()).isTrue();
        var captor=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://api.github.com/user");
        assertThat(captor.getValue().headers().firstValue("Authorization")).contains("Bearer private-token");
        doReturn(response(401,"private-token")).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(()->api.get(c,"private-token","/user")).hasMessageContaining("凭证失效").hasMessageNotContaining("private-token");
        assertThatThrownBy(()->api.get(c,"private-token","//evil.test")).hasMessageContaining("路径");
        assertThatThrownBy(()->api.get(c,"private-token","/../user")).hasMessageContaining("路径");
    }
    @Test void gitlabUsesPrivateTokenAndRejectsRedirectResponse() throws Exception {
        doReturn(response(302,"secret")).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(()->api.get(connection("GITLAB","https://gitlab.com"),"secret","/user")).hasMessageContaining("不可读取").hasMessageNotContaining("secret");
        var captor=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(captor.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(captor.getValue().uri().toString()).isEqualTo("https://gitlab.com/api/v4/user");
        assertThat(captor.getValue().headers().firstValue("PRIVATE-TOKEN")).contains("secret");
    }
    @Test void rejectsOversizedBodyAndStopsInterruptedRequests() throws Exception {
        doReturn(response(200,"x".repeat(2_000_001))).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(()->api.get(connection("GITHUB","https://github.com"),"token","/user")).hasMessageContaining("大小");
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException());
        try { assertThatThrownBy(()->api.get(connection("GITHUB","https://github.com"),"token","/user")).hasMessageContaining("中止");assertThat(Thread.currentThread().isInterrupted()).isTrue(); }
        finally { Thread.interrupted(); }
    }
    @Test void retriesIoFailuresWithinBound() throws Exception {
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new java.io.IOException("secret"));
        assertThatThrownBy(()->api.get(connection("GITHUB","https://github.com"),"token","/user")).hasMessageContaining("无法连接").hasMessageNotContaining("secret");
        verify(client,times(3)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThat(RemoteApi.encode("a/b c")).isEqualTo("a%2Fb%20c");
    }
    @Test void normalizesTrailingSlashesWithoutBacktrackingOrRemovingInnerPaths() {
        assertThat(api.normalize(IntegrationProvider.GITLAB,"https://gitlab.com" + "/".repeat(100_000)))
                .isEqualTo("https://gitlab.com");
        assertThat(api.normalize(IntegrationProvider.GITLAB,"https://gitlab.example.test/team///"))
                .isEqualTo("https://gitlab.example.test/team");
        assertThatThrownBy(() -> api.normalize(IntegrationProvider.GITLAB,"https://gitlab.com/other///"))
                .hasMessageContaining("允许范围");
    }
    @Test void rateLimitRetriesThenReturnsSuccessfulResponse() throws Exception {
        HttpResponse<String> limited=response(403,"secret");
        HttpResponse<String> success=response(200,"{\"ok\":true}");
        when(limited.headers()).thenReturn(HttpHeaders.of(Map.of("X-RateLimit-Remaining",List.of("0")),(a,b)->true));
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class)))
                .thenReturn(limited,success);
        assertThat(api.get(connection("GITHUB","https://github.com"),"secret","/user").path("ok").asBoolean()).isTrue();
        verify(client,times(2)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
    @Test void transientServerFailuresStopAtRetryBoundWithRedactedMessage() throws Exception {
        doReturn(response(503,"secret")).when(client).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        assertThatThrownBy(() -> api.get(connection("GITHUB","https://github.com"),"secret","/user"))
                .hasMessage("平台暂时不可用");
        verify(client,times(3)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }
}

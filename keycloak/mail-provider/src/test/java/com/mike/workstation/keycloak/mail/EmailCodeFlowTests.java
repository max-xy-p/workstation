package com.mike.workstation.keycloak.mail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.ws.rs.core.MultivaluedHashMap;

import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.common.ClientConnection;
import org.keycloak.crypto.KeyWrapper;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeyManager;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.RootAuthenticationSessionModel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

class EmailCodeFlowTests {
    private final EmailCodeServiceTests.Fixture fixture = new EmailCodeServiceTests.Fixture();
    private final KeycloakSession session = mock(KeycloakSession.class);
    private final RealmModel realm = mock(RealmModel.class);
    private final UserModel user = mock(UserModel.class);
    private final AuthenticationSessionModel auth = mock(AuthenticationSessionModel.class);
    private final EmailSenderProvider mail = mock(EmailSenderProvider.class);
    private final Map<String, String> notes = new HashMap<>();
    private String capturedCode;
    private Map<String, String> capturedConfig;

    EmailCodeFlowTests() throws Exception {
        when(session.singleUseObjects()).thenReturn(fixture.store);
        var keys = mock(KeyManager.class);
        var key = new KeyWrapper();
        key.setSecretKey(fixture.key);
        when(keys.getActiveKey(any(), any(), anyString())).thenReturn(key);
        when(session.keys()).thenReturn(keys);
        when(session.getProvider(EmailSenderProvider.class)).thenReturn(mail);
        when(realm.getId()).thenReturn("realm");
        when(realm.getAttribute(EmailCodeFlow.ENABLED_ATTRIBUTE)).thenReturn("true");
        when(realm.getSmtpConfig()).thenReturn(Map.of("from", "noreply@example.com"));
        when(user.getId()).thenReturn("user");
        when(user.isEnabled()).thenReturn(true);
        when(user.getEmail()).thenReturn("user@example.com");
        var root = mock(RootAuthenticationSessionModel.class);
        when(root.getId()).thenReturn("root");
        var client = mock(ClientModel.class);
        when(client.getId()).thenReturn("client");
        when(auth.getParentSession()).thenReturn(root);
        when(auth.getTabId()).thenReturn("tab");
        when(auth.getClient()).thenReturn(client);
        when(auth.getAuthNote(anyString())).thenAnswer(call -> notes.get(call.getArgument(0)));
        doAnswer(
                        call -> {
                            notes.put(call.getArgument(0), call.getArgument(1));
                            return null;
                        })
                .when(auth)
                .setAuthNote(anyString(), anyString());
        doAnswer(
                        call -> {
                            capturedCode =
                                    ((String) call.getArgument(3))
                                            .replaceAll("[^0-9]", "")
                                            .substring(0, 6);
                            capturedConfig = Map.copyOf(call.getArgument(0));
                            return null;
                        })
                .when(mail)
                .send(anyMap(), anyString(), anyString(), anyString(), isNull());
    }

    private RequiredActionContext requiredContext(String operation, String code) {
        var context = mock(RequiredActionContext.class);
        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(realm);
        when(context.getUser()).thenReturn(user);
        when(context.getAuthenticationSession()).thenReturn(auth);
        when(context.form()).thenReturn(mock(LoginFormsProvider.class, RETURNS_SELF));
        var connection = mock(ClientConnection.class);
        when(connection.getRemoteAddr()).thenReturn("127.0.0.1");
        when(context.getConnection()).thenReturn(connection);
        var request = mock(HttpRequest.class);
        var form = new MultivaluedHashMap<String, String>();
        form.putSingle("operation", operation);
        if (code != null) {
            form.putSingle("code", code);
        }
        when(request.getDecodedFormParameters()).thenReturn(form);
        when(context.getHttpRequest()).thenReturn(request);
        return context;
    }

    @Test
    void registrationMarksEmailVerifiedOnlyAfterCorrectCodeAndRejectsReplay() {
        var provider = new EmailCodeRequiredAction();
        provider.requiredActionChallenge(requiredContext("verify", null));
        var wrong = requiredContext("verify", "bad");
        provider.processAction(wrong);
        verify(user, never()).setEmailVerified(true);
        var correct = requiredContext("verify", capturedCode);
        provider.processAction(correct);
        verify(correct).success();
        verify(user).setEmailVerified(true);
        provider.processAction(requiredContext("verify", capturedCode));
        verify(user, times(1)).setEmailVerified(true);
        assertTrue(
                Instant.parse(capturedConfig.get(WorkstationEmailSender.CODE_EXPIRES_AT))
                        .isBefore(Instant.now().plusSeconds(301)));
    }

    @Test
    void resetUsesSeparatePurposeAndEndsFlowAfterNativePasswordUpdate() {
        var required = new EmailCodeRequiredAction();
        required.requiredActionChallenge(requiredContext("verify", null));
        String registrationCode = capturedCode;
        var flow = new EmailCodeFlow(session, realm, auth, user, "RESET");
        assertFalse(flow.verify(registrationCode));
        assertNull(flow.send(false, "127.0.0.1"));
        var context = mock(AuthenticationFlowContext.class);
        when(context.getSession()).thenReturn(session);
        when(context.getRealm()).thenReturn(realm);
        when(context.getUser()).thenReturn(user);
        when(context.getAuthenticationSession()).thenReturn(auth);
        var submittedRequest = requiredContext("verify", capturedCode).getHttpRequest();
        when(context.getHttpRequest()).thenReturn(submittedRequest);
        new EmailCodeResetAuthenticator().action(context);
        verify(context).success();
        assertEquals("true", notes.get(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS));
    }

    @Test
    void nonexistentDisabledAndChangedEmailCannotPassVerification() {
        var flow = new EmailCodeFlow(session, realm, auth, user, "VERIFY");
        flow.send(false, "127.0.0.1");
        when(user.getEmail()).thenReturn("changed@example.com");
        assertFalse(flow.verify(capturedCode));
        when(user.getEmail()).thenReturn("user@example.com");
        when(user.isEnabled()).thenReturn(false);
        assertFalse(flow.verify(capturedCode));
        var missing = new EmailCodeFlow(session, realm, auth, null, "RESET");
        assertNull(missing.send(false, "127.0.0.1"));
        assertFalse(missing.verify(capturedCode));
    }

    @Test
    void failedSubmissionInvalidatesCodeAndResetDoesNotExposeFailure() throws Exception {
        doThrow(new EmailException("下游发送失败"))
                .when(mail)
                .send(anyMap(), anyString(), anyString(), anyString(), isNull());
        var registration = new EmailCodeFlow(session, realm, auth, user, "VERIFY");
        assertNotNull(registration.send(false, "127.0.0.1"));
        var reset = new EmailCodeFlow(session, realm, auth, user, "RESET");
        assertNull(reset.send(false, "127.0.0.1"));
        assertNull(reset.send(true, "127.0.0.1"));
        assertFalse(reset.verify("123456"));
    }

    @Test
    void factoriesExposeVersionMarkerAndOverrideOnlyNativeMatchingIds() {
        var verifyFactory = new EmailCodeRequiredAction();
        var resetFactory = new EmailCodeResetAuthenticator();
        assertEquals("VERIFY_EMAIL", verifyFactory.getId());
        assertEquals("reset-credential-email", resetFactory.getId());
        assertTrue(verifyFactory.order() > 0);
        assertEquals("1", resetFactory.getOperationalInfo().get("workstationEmailCodeVersion"));
        assertFalse(EmailCodeFlow.enabled(mock(RealmModel.class)));
    }
}

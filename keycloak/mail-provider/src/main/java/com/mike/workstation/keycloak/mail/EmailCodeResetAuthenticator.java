package com.mike.workstation.keycloak.mail;

import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.authenticators.resetcred.ResetCredentialEmail;
import org.keycloak.provider.ServerInfoAwareProviderFactory;
import org.keycloak.services.managers.AuthenticationManager;

import java.util.Map;

/** 只在证明邮箱所有权后推进原生重置流程，不签发登录凭据或自行更新密码。 */
public final class EmailCodeResetAuthenticator extends ResetCredentialEmail
        implements ServerInfoAwareProviderFactory {
    @Override
    public Map<String, String> getOperationalInfo() {
        return Map.of("workstationEmailCodeVersion", "1");
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        if (!EmailCodeFlow.enabled(context.getRealm())) {
            super.authenticate(context);
            return;
        }
        EmailCodeFlow flow = flow(context);
        context.challenge(
                flow.form(context.form(), flow.send(false, context.getConnection().getRemoteAddr()))
                        .createForm(EmailCodeFlow.TEMPLATE));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        if (!EmailCodeFlow.enabled(context.getRealm())) {
            super.action(context);
            return;
        }
        EmailCodeFlow flow = flow(context);
        var parameters = context.getHttpRequest().getDecodedFormParameters();
        if ("resend".equals(parameters.getFirst("operation"))) {
            context.challenge(
                    flow.form(
                                    context.form(),
                                    flow.send(true, context.getConnection().getRemoteAddr()))
                            .createForm(EmailCodeFlow.TEMPLATE));
        } else if (flow.verify(parameters.getFirst("code"))) {
            context.getUser().setEmailVerified(true);
            // 原生 UPDATE_PASSWORD 完成后结束本次流程，必须重新登录。
            context.getAuthenticationSession()
                    .setAuthNote(AuthenticationManager.END_AFTER_REQUIRED_ACTIONS, "true");
            context.success();
        } else {
            context.challenge(
                    flow.form(context.form(), EmailCodeFlow.INVALID)
                            .createForm(EmailCodeFlow.TEMPLATE));
        }
    }

    private EmailCodeFlow flow(AuthenticationFlowContext context) {
        return new EmailCodeFlow(
                context.getSession(),
                context.getRealm(),
                context.getAuthenticationSession(),
                context.getUser(),
                "RESET");
    }
}

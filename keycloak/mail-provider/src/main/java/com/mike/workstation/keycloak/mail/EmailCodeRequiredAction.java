package com.mike.workstation.keycloak.mail;

import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.requiredactions.VerifyEmail;
import org.keycloak.provider.ServerInfoAwareProviderFactory;

import java.util.Map;

/** 保留原生 VERIFY_EMAIL 动作标识，只有显式启用的 Realm 使用验证码。 */
public final class EmailCodeRequiredAction extends VerifyEmail
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
    public void requiredActionChallenge(RequiredActionContext context) {
        if (!EmailCodeFlow.enabled(context.getRealm())) {
            super.requiredActionChallenge(context);
            return;
        }
        if (context.getUser().isEmailVerified()) {
            context.success();
            return;
        }
        EmailCodeFlow flow = flow(context);
        context.challenge(
                flow.form(context.form(), flow.send(false, context.getConnection().getRemoteAddr()))
                        .createForm(EmailCodeFlow.TEMPLATE));
    }

    @Override
    public void processAction(RequiredActionContext context) {
        if (!EmailCodeFlow.enabled(context.getRealm())) {
            super.processAction(context);
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
            context.success();
        } else {
            context.challenge(
                    flow.form(context.form(), EmailCodeFlow.INVALID)
                            .createForm(EmailCodeFlow.TEMPLATE));
        }
    }

    private EmailCodeFlow flow(RequiredActionContext context) {
        return new EmailCodeFlow(
                context.getSession(),
                context.getRealm(),
                context.getAuthenticationSession(),
                context.getUser(),
                "VERIFY");
    }
}

package com.mike.workstation.keycloak.mail;

import org.keycloak.crypto.Algorithm;
import org.keycloak.crypto.KeyUse;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailSenderProvider;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.HashMap;
import java.util.Map;

/** 两种认证用途共用发信与表单，绑定值仍包含用途、用户、邮箱及认证会话。 */
final class EmailCodeFlow {
    private static final String RESET_PURPOSE = "RESET";
    static final String ENABLED_ATTRIBUTE = "workstation.email-code.enabled";
    static final String INVALID = "验证码无效、已过期或尝试次数已达上限，请重新获取。";
    static final String TEMPLATE = "workstation-email-code.ftl";
    private final KeycloakSession session;
    private final RealmModel realm;
    private final AuthenticationSessionModel authentication;
    private final UserModel user;
    private final String purpose;
    private final EmailCodeService service;

    EmailCodeFlow(
            KeycloakSession session,
            RealmModel realm,
            AuthenticationSessionModel authentication,
            UserModel user,
            String purpose) {
        this.session = session;
        this.realm = realm;
        this.authentication = authentication;
        this.user = user;
        this.purpose = purpose;
        this.service =
                new EmailCodeService(
                        session.singleUseObjects(),
                        session.keys()
                                .getActiveKey(realm, KeyUse.SIG, Algorithm.HS256)
                                .getSecretKey());
    }

    static boolean enabled(RealmModel realm) {
        return Boolean.parseBoolean(realm.getAttribute(ENABLED_ATTRIBUTE));
    }

    private String note() {
        return "workstation-code-" + purpose;
    }

    private EmailCodeService.Binding binding() {
        String flow =
                authentication.getParentSession().getId()
                        + "|"
                        + authentication.getTabId()
                        + "|"
                        + authentication.getClient().getId();
        return new EmailCodeService.Binding(
                realm.getId(),
                user == null ? "absent:" + flow : user.getId(),
                user == null || user.getEmail() == null ? "" : user.getEmail(),
                flow,
                purpose);
    }

    String send(boolean resend, String remoteAddress) {
        EmailCodeService.Issued issued;
        try {
            issued =
                    service.issue(
                            binding(), authentication.getAuthNote(note()), resend, remoteAddress);
        } catch (IllegalStateException error) {
            return RESET_PURPOSE.equals(purpose) ? null : error.getMessage();
        }
        if (issued == null) {
            return null;
        }
        authentication.setAuthNote(note(), issued.id());
        if (user == null
                || !user.isEnabled()
                || user.getEmail() == null
                || user.getEmail().isBlank()) {
            // 找回密码对未知或不可用账号显示相同页面，不发送邮件，也不能验证成功。
            service.invalidate(issued.id());
            return null;
        }
        try {
            Map<String, String> config = new HashMap<>(realm.getSmtpConfig());
            config.put(WorkstationEmailSender.CODE_EXPIRES_AT, issued.expiresAt().toString());
            String subject =
                    RESET_PURPOSE.equals(purpose) ? "Workstation 重置密码验证码" : "Workstation 邮箱验证码";
            String text =
                    "你的"
                            + subject
                            + "是："
                            + issued.value()
                            + "。有效期 5 分钟，请在刚才的页面输入。请勿向他人提供验证码；如果不是你本人操作，请忽略此邮件。";
            session.getProvider(EmailSenderProvider.class)
                    .send(config, user.getEmail(), subject, text, null);
            return null;
        } catch (EmailException error) {
            service.invalidate(issued.id());
            // 重置入口的页面不能通过发送结果泄露账号是否存在。
            return RESET_PURPOSE.equals(purpose) ? null : "暂时无法提交验证邮件，请稍后重新获取。";
        }
    }

    boolean verify(String code) {
        return user != null
                && user.isEnabled()
                && user.getEmail() != null
                && service.verify(binding(), authentication.getAuthNote(note()), code);
    }

    LoginFormsProvider form(LoginFormsProvider form, String error) {
        form.setAttribute("codeTitle", RESET_PURPOSE.equals(purpose) ? "验证邮箱以重置密码" : "验证你的邮箱")
                .setAttribute("codeDescription", "如账号存在且可用，验证码将发送到对应邮箱。请在此页面输入，5 分钟内有效。")
                .setAttribute("codeHelp", "60 秒内请勿重复发送；最多尝试 5 次，重发后旧码失效。");
        if (error != null) {
            form.setError(error);
        }
        return form;
    }
}

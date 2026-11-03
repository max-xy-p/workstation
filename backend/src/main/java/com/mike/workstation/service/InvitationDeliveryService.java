package com.mike.workstation.service;

import com.mike.workstation.assembler.ApiTime;
import com.mike.workstation.config.InvitationProperties;
import com.mike.workstation.mapper.InvitationMapper;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 网络调用不占用数据库事务；重复调用由邮件服务的稳定 requestKey 吸收。 */
@Service
public class InvitationDeliveryService {
    private final InvitationMapper mapper;
    private final InvitationTokenService tokens;
    private final InvitationMailClient mail;
    private final InvitationProperties properties;

    public InvitationDeliveryService(InvitationMapper mapper, InvitationTokenService tokens,
            InvitationMailClient mail, InvitationProperties properties) {
        this.mapper = mapper;
        this.tokens = tokens;
        this.mail = mail;
        this.properties = properties;
    }

    public void deliver(String id) throws Exception {
        var invitation = mapper.findDelivery(id);
        if (invitation == null || !("PENDING".equals(invitation.getMailStatus())
                || "PUBLISHED".equals(invitation.getMailStatus()))) {
            return;
        }
        if (!invitation.getExpiresAt().isAfter(LocalDateTime.now(ZoneId.systemDefault()))) {
            mapper.completed(id, "EXPIRED");
            return;
        }
        if (!"PENDING".equals(invitation.getStatus())) {
            mapper.completed(id, "CANCELLED");
            return;
        }
        String token = tokens.token(invitation.getId());
        if (!tokens.hash(token).equals(invitation.getTokenHash())) {
            mapper.completed(id, "CANCELLED");
            return;
        }
        String link = properties.publicUrl().replaceAll("/+$", "") + "/invitations#" + token;
        String text = "你受邀加入 Workstation 工作空间「" + invitation.getWorkspaceName()
                + "」。\n\n请使用收件邮箱注册或登录，再打开以下链接并确认接受：\n" + link
                + "\n\n邀请有效期为创建后 23 小时；如非本人预期，请忽略此邮件。";
        mail.enqueue(new InvitationMailClient.MailRequest("workspace-invitation/" + invitation.getId(),
                invitation.getEmail(), "Workstation 工作空间邀请", text,
                ApiTime.toOffset(invitation.getExpiresAt())));
        mapper.completed(id, "ACCEPTED");
    }
}

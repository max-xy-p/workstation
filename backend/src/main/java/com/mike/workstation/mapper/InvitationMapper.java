package com.mike.workstation.mapper;

import com.mike.workstation.domain.WorkspaceInvitation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

/** 邀请与专用邮件 Outbox；不复用审计事件作为邮件任务。 */
@Mapper
public interface InvitationMapper {
    String PROJECTION = """
            SELECT i.*, o.id AS delivery_id, o.state AS mail_status
            FROM workspace_invitations i JOIN invitation_mail_outbox o ON o.invitation_id = i.id
            """;

    @Select(PROJECTION + " WHERE i.id = #{id}")
    WorkspaceInvitation find(String id);

    @Select(PROJECTION + " WHERE i.workspace_id = #{workspaceId} AND i.created_by = #{actor} AND i.request_id = #{requestId}")
    WorkspaceInvitation findRequest(@Param("workspaceId") Long workspaceId,
            @Param("actor") String actor, @Param("requestId") String requestId);

    @Select(PROJECTION + " WHERE i.workspace_id = #{workspaceId} ORDER BY i.created_at DESC, i.id DESC LIMIT 100")
    List<WorkspaceInvitation> list(Long workspaceId);

    @Select("""
            SELECT COUNT(*) FROM workspace_invitations
            WHERE workspace_id = #{workspaceId} AND status = 'PENDING' AND expires_at > #{now}
            """)
    int countPending(@Param("workspaceId") Long workspaceId, @Param("now") LocalDateTime now);

    @Select("""
            SELECT COUNT(*) FROM workspace_invitations
            WHERE workspace_id = #{workspaceId} AND email = #{email}
                AND status = 'PENDING' AND expires_at > #{now}
            """)
    int countEmail(@Param("workspaceId") Long workspaceId, @Param("email") String email,
            @Param("now") LocalDateTime now);

    @Insert("""
            INSERT INTO workspace_invitations
            (id, workspace_id, workspace_name, email, role, status, created_by, request_id,
             token_hash, created_at, expires_at)
            VALUES (#{id}, #{workspaceId}, #{workspaceName}, #{email}, #{role}, 'PENDING',
                #{createdBy}, #{requestId}, #{tokenHash}, #{createdAt}, #{expiresAt})
            """)
    int insert(WorkspaceInvitation invitation);

    @Insert("""
            INSERT INTO invitation_mail_outbox (id, invitation_id, state, next_attempt_at)
            VALUES (#{deliveryId}, #{id}, 'PENDING', #{createdAt})
            """)
    int insertOutbox(WorkspaceInvitation invitation);

    @Update("""
            UPDATE workspace_invitations SET status = #{status}, accepted_by = #{subject}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int transition(@Param("id") String id, @Param("status") String status,
            @Param("subject") String subject);

    @Update("""
            UPDATE invitation_mail_outbox SET state = 'CANCELLED', lease_token = NULL
            WHERE invitation_id = #{id} AND state NOT IN ('ACCEPTED', 'EXPIRED')
            """)
    int cancel(String id);

    @Select("""
            SELECT o.id FROM invitation_mail_outbox o JOIN workspace_invitations i ON i.id = o.invitation_id
            WHERE o.state IN ('PENDING', 'PUBLISHED') AND o.next_attempt_at <= #{now}
            ORDER BY o.next_attempt_at, o.id LIMIT 20
            """)
    List<String> due(LocalDateTime now);

    @Update("""
            UPDATE invitation_mail_outbox SET lease_token = #{lease}, next_attempt_at = #{until}
            WHERE id = #{id} AND state IN ('PENDING', 'PUBLISHED') AND next_attempt_at <= #{now}
            """)
    int claim(@Param("id") String id, @Param("lease") String lease,
            @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    @Select(PROJECTION + " WHERE o.id = #{id}")
    WorkspaceInvitation findDelivery(String id);

    @Update("""
            UPDATE invitation_mail_outbox SET state = #{state}, next_attempt_at = #{next},
                lease_token = NULL
            WHERE id = #{id} AND lease_token = #{lease} AND state IN ('PENDING', 'PUBLISHED')
            """)
    int published(@Param("id") String id, @Param("lease") String lease,
            @Param("state") String state, @Param("next") LocalDateTime next);

    @Update("""
            UPDATE invitation_mail_outbox SET state = #{state}, lease_token = NULL
            WHERE id = #{id} AND state IN ('PENDING', 'PUBLISHED')
            """)
    int completed(@Param("id") String id, @Param("state") String state);
}

package com.mike.workstation.mapper;

import com.mike.workstation.domain.AuditEvent;
import com.mike.workstation.domain.AuditResult;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 只追加的工作空间审计事件数据访问。 */
@Mapper
public interface AuditMapper {
    @Insert(
            """
            INSERT INTO audit_events
                (event_id, workspace_id, actor_subject, actor_type, action, resource_type,
                 resource_id, result, occurred_at, trace_id)
            VALUES
                (#{eventId}, #{workspaceId}, #{actorSubject}, #{actorType}, #{action}, #{resourceType},
                 #{resourceId}, #{result}, #{occurredAt}, #{traceId})
            ON DUPLICATE KEY UPDATE event_id = VALUES(event_id)
            """)
    /* event_id 唯一；重复消息执行无副作用更新，从而让消费者能够安全 ACK。 */
    int insert(AuditEvent event);

    @Select(
            """
            <script>
            SELECT id, event_id, workspace_id, actor_subject, actor_type, action, resource_type,
                   resource_id, result, occurred_at, trace_id
            FROM audit_events
            WHERE workspace_id = #{workspaceId}
              <if test="actorSubject != null and actorSubject != ''">AND actor_subject = #{actorSubject}</if>
              <if test="action != null and action != ''">AND action = #{action}</if>
              <if test="resourceType != null and resourceType != ''">AND resource_type = #{resourceType}</if>
              <if test="result != null">AND result = #{result}</if>
            ORDER BY occurred_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<AuditEvent> listPage(
            @Param("workspaceId") Long workspaceId,
            @Param("actorSubject") String actorSubject,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("result") AuditResult result,
            @Param("offset") long offset,
            @Param("size") int size);

    @Select(
            """
            <script>
            SELECT COUNT(*) FROM audit_events
            WHERE workspace_id = #{workspaceId}
              <if test="actorSubject != null and actorSubject != ''">AND actor_subject = #{actorSubject}</if>
              <if test="action != null and action != ''">AND action = #{action}</if>
              <if test="resourceType != null and resourceType != ''">AND resource_type = #{resourceType}</if>
              <if test="result != null">AND result = #{result}</if>
            </script>
            """)
    long count(
            @Param("workspaceId") Long workspaceId,
            @Param("actorSubject") String actorSubject,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("result") AuditResult result);
}

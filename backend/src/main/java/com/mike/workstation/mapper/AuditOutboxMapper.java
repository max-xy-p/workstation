package com.mike.workstation.mapper;

import com.mike.workstation.domain.AuditOutbox;
import com.mike.workstation.domain.AuditOutboxStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 审计 Outbox 的写入、带租约领取和投递状态更新。 */
@Mapper
public interface AuditOutboxMapper {
    @Insert("""
            INSERT INTO audit_outbox
                (event_id, workspace_id, actor_subject, actor_type, action, resource_type,
                 resource_id, result, occurred_at, trace_id, status, attempts,
                 next_attempt_at, created_at)
            VALUES
                (#{eventId}, #{workspaceId}, #{actorSubject}, #{actorType}, #{action}, #{resourceType},
                 #{resourceId}, #{result}, #{occurredAt}, #{traceId}, #{status}, #{attempts},
                 #{nextAttemptAt}, #{createdAt})
            """)
    int insert(AuditOutbox event);

    @Select("""
            SELECT id, event_id, workspace_id, actor_subject, actor_type, action, resource_type,
                   resource_id, result, occurred_at, trace_id, status, attempts,
                   next_attempt_at, locked_by, locked_until, last_error, created_at, published_at
            FROM audit_outbox
            WHERE (status = 'PENDING' AND #{now} >= next_attempt_at)
               OR (status = 'SENDING' AND #{now} >= locked_until)
            ORDER BY id
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    /*
     * PENDING 到达重试时间后可以领取；SENDING 租约过期说明上一个 Relay 可能已崩溃，也可重新领取。
     * SKIP LOCKED 使并发 Relay 跳过别人已经锁住的行，而不是互相等待。
     */
    List<AuditOutbox> findClaimable(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("""
            <script>
            UPDATE audit_outbox
            SET status = 'SENDING',
                attempts = attempts + 1,
                locked_by = #{lockedBy},
                locked_until = #{lockedUntil},
                last_error = NULL
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            </script>
            """)
    int markSending(
            @Param("ids") List<Long> ids,
            @Param("lockedBy") String lockedBy,
            @Param("lockedUntil") LocalDateTime lockedUntil
    );

    @Update("""
            UPDATE audit_outbox
            SET status = 'PUBLISHED',
                published_at = #{publishedAt},
                locked_by = NULL,
                locked_until = NULL,
                last_error = NULL
            WHERE id = #{id} AND status = 'SENDING' AND locked_by = #{lockedBy}
            """)
    /* 只有持有当前 lockedBy 的 Relay 才能完成状态迁移，避免过期执行器覆盖新执行器结果。 */
    int markPublished(
            @Param("id") Long id,
            @Param("lockedBy") String lockedBy,
            @Param("publishedAt") LocalDateTime publishedAt
    );

    @Update("""
            UPDATE audit_outbox
            SET status = #{status},
                next_attempt_at = #{nextAttemptAt},
                locked_by = NULL,
                locked_until = NULL,
                last_error = #{lastError}
            WHERE id = #{id} AND status = 'SENDING' AND locked_by = #{lockedBy}
            """)
    int markFailure(
            @Param("id") Long id,
            @Param("lockedBy") String lockedBy,
            @Param("status") AuditOutboxStatus status,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError
    );
}

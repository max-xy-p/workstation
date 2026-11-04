package com.mike.workstation.mapper;

import com.mike.workstation.domain.Notification;
import com.mike.workstation.dto.NotificationState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import java.time.LocalDateTime;
import java.util.List;

/** 所有面向用户的读取与写入都同时限定接收者和当前空间成员关系。 */
@Mapper
public interface NotificationMapper {
    String VISIBLE = """
            FROM notifications n JOIN workspace_members wm
            ON wm.workspace_id = n.workspace_id AND wm.principal_subject = n.recipient
            WHERE n.recipient = #{recipient}
            """;

    @Select("""
            SELECT TRUE AS enabled,
                (SELECT COUNT(*) FROM notifications n JOIN workspace_members wm
                    ON wm.workspace_id = n.workspace_id AND wm.principal_subject = n.recipient
                    WHERE n.recipient = #{recipient} AND n.read_at IS NULL) AS unread_count,
                COALESCE((SELECT last_sequence FROM notification_inboxes WHERE recipient = #{recipient}), 0) AS latest_sequence,
                COALESCE((SELECT revision FROM notification_inboxes WHERE recipient = #{recipient}), 0) AS revision
            """)
    NotificationState state(String recipient);

    @Select("<script> SELECT n.* " + VISIBLE
            + " AND n.sequence &lt;= #{upper} <if test='unreadOnly'>AND n.read_at IS NULL</if>"
            + " ORDER BY n.sequence DESC LIMIT #{size} OFFSET #{offset} </script>")
    List<Notification> list(@Param("recipient") String recipient, @Param("upper") long upper,
            @Param("unreadOnly") boolean unreadOnly, @Param("offset") long offset, @Param("size") int size);

    @Select("<script> SELECT COUNT(*) " + VISIBLE
            + " AND n.sequence &lt;= #{upper} <if test='unreadOnly'>AND n.read_at IS NULL</if> </script>")
    long count(@Param("recipient") String recipient, @Param("upper") long upper,
            @Param("unreadOnly") boolean unreadOnly);

    @Select("SELECT n.* " + VISIBLE + " AND n.id = #{id}")
    Notification find(@Param("recipient") String recipient, @Param("id") String id);

    @Insert("INSERT IGNORE INTO notification_inboxes (recipient, last_sequence, revision) VALUES (#{recipient}, 0, 0)")
    int ensureInbox(String recipient);

    @Select("SELECT last_sequence FROM notification_inboxes WHERE recipient = #{recipient} FOR UPDATE")
    Long lockInbox(String recipient);

    @Update("""
            UPDATE notification_inboxes SET last_sequence = last_sequence + 1, revision = revision + 1, signal_pending = TRUE
            WHERE recipient = #{recipient}
            """)
    int advance(String recipient);

    @Update("UPDATE notification_inboxes SET revision = revision + 1, signal_pending = TRUE WHERE recipient = #{recipient}")
    int changed(String recipient);

    @Select("""
            <script>
            SELECT wm.principal_subject FROM workspace_members wm
            JOIN security_principals sp ON sp.subject_id = wm.principal_subject
            WHERE wm.workspace_id = #{workspaceId} AND sp.principal_type = 'USER'
                AND wm.principal_subject IN
                <foreach collection="recipients" item="recipient" open="(" close=")" separator=",">
                    #{recipient}
                </foreach>
            ORDER BY wm.principal_subject FOR SHARE OF wm
            </script>
            """)
    List<String> eligibleRecipients(@Param("workspaceId") Long workspaceId,
            @Param("recipients") List<String> recipients);

    @Insert("""
            INSERT INTO notifications
            (id, event_id, recipient, sequence, workspace_id, type, title, body,
                resource_type, resource_id, created_at)
            VALUES (#{id}, #{eventId}, #{recipient}, #{sequence}, #{workspaceId}, #{type}, #{title},
                #{body}, #{resourceType}, #{resourceId}, #{createdAt})
            """)
    int insert(Notification notification);

    @Update("""
            UPDATE notifications n JOIN workspace_members wm
                ON wm.workspace_id = n.workspace_id AND wm.principal_subject = n.recipient
            SET n.read_at = #{now}
            WHERE n.recipient = #{recipient} AND n.id = #{id} AND n.read_at IS NULL
            """)
    int read(@Param("recipient") String recipient, @Param("id") String id,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE notifications n JOIN workspace_members wm
                ON wm.workspace_id = n.workspace_id AND wm.principal_subject = n.recipient
            SET n.read_at = #{now}
            WHERE n.recipient = #{recipient} AND n.sequence <= #{upper} AND n.read_at IS NULL
            """)
    int readAll(@Param("recipient") String recipient, @Param("upper") long upper,
            @Param("now") LocalDateTime now);

    @Delete("DELETE FROM notifications WHERE created_at < #{before} LIMIT 1000")
    int purge(LocalDateTime before);
}

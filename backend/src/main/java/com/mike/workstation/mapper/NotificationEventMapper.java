package com.mike.workstation.mapper;

import com.mike.workstation.domain.NotificationEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import java.time.LocalDateTime;

/** 事件元数据与通知在同一业务事务中提交。 */
@Mapper
public interface NotificationEventMapper {
    @Insert("""
            INSERT IGNORE INTO notification_events (event_id, workspace_id, fingerprint, created_at)
            VALUES (#{eventId}, #{workspaceId}, #{fingerprint}, #{createdAt})
            """)
    int insert(NotificationEvent event);

    @Select("SELECT * FROM notification_events WHERE event_id = #{id} FOR UPDATE")
    NotificationEvent lock(String id);

    @Delete("DELETE FROM notification_events WHERE created_at < #{before} LIMIT 1000")
    int purge(LocalDateTime before);
}

package com.mike.workstation.mapper;

import com.mike.workstation.domain.NotificationRefresh;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;
import java.util.List;

/** 收件箱的待发布标记只补偿刷新消息，不承担通知生成。 */
@Mapper
public interface NotificationSignalMapper {
    @Select("""
            SELECT recipient FROM notification_inboxes WHERE signal_pending = TRUE
                AND signal_next_attempt_at <= #{now} ORDER BY signal_next_attempt_at, recipient LIMIT 100
            """)
    List<String> due(LocalDateTime now);

    @Update("""
            UPDATE notification_inboxes SET signal_lease_token = #{lease}, signal_next_attempt_at = #{until}
            WHERE recipient = #{recipient} AND signal_pending = TRUE AND signal_next_attempt_at <= #{now}
            """)
    int claim(@Param("recipient") String recipient, @Param("lease") String lease,
            @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    @Select("SELECT recipient, revision FROM notification_inboxes WHERE recipient = #{recipient}")
    NotificationRefresh snapshot(String recipient);

    @Update("""
            UPDATE notification_inboxes
            SET signal_pending = (revision > #{revision}), signal_lease_token = NULL, signal_next_attempt_at = #{now}
            WHERE recipient = #{recipient} AND signal_lease_token = #{lease}
            """)
    int published(@Param("recipient") String recipient, @Param("lease") String lease,
            @Param("revision") long revision, @Param("now") LocalDateTime now);
}

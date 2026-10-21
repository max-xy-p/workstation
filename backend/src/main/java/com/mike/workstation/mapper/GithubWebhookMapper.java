package com.mike.workstation.mapper;

import com.mike.workstation.domain.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 投递收件箱与指定来源目标持久化；执行仍复用原同步队列。 */
@Mapper
public interface GithubWebhookMapper {
    @Select("SELECT * FROM integration_webhooks WHERE id=#{id}")
    GithubWebhook endpoint(String id);
    @Select("SELECT * FROM integration_webhooks WHERE workspace_id=#{w} AND connection_id=#{c} FOR UPDATE")
    GithubWebhook lock(@Param("w") Long w,@Param("c") Long c);
    @Select("SELECT * FROM integration_webhooks WHERE workspace_id=#{w} AND connection_id=#{c}")
    GithubWebhook get(@Param("w") Long w,@Param("c") Long c);
    @Insert("""
            INSERT INTO integration_webhooks(id,workspace_id,connection_id,secret,key_version,enabled,version)
            VALUES(#{id},#{workspaceId},#{connectionId},#{secret},#{keyVersion},#{enabled},#{version})
            ON DUPLICATE KEY UPDATE secret=VALUES(secret),key_version=VALUES(key_version),enabled=VALUES(enabled),version=VALUES(version)
            """)
    int save(GithubWebhook hook);
    @Select("SELECT * FROM github_webhook_deliveries WHERE webhook_id=#{hook} AND delivery_key=#{key} FOR UPDATE")
    GithubWebhookDelivery duplicate(@Param("hook") String hook,@Param("key") String key);
    @Insert("""
            INSERT INTO github_webhook_deliveries(webhook_id,workspace_id,connection_id,webhook_version,delivery_key,body_hash,event_type,action,
                source_id,link_token,status,received_at,next_attempt_at)
            VALUES(#{webhookId},#{workspaceId},#{connectionId},#{webhookVersion},#{deliveryKey},#{bodyHash},#{eventType},#{action},
                #{sourceId},#{linkToken},#{status},#{receivedAt},#{nextAttemptAt})
            """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insert(GithubWebhookDelivery delivery);
    @Select("SELECT * FROM github_webhook_deliveries WHERE workspace_id=#{w} AND connection_id=#{c} ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    List<GithubWebhookDelivery> list(@Param("w") Long w,@Param("c") Long c,@Param("offset") long offset,@Param("size") int size);
    @Select("SELECT COUNT(*) FROM github_webhook_deliveries WHERE workspace_id=#{w} AND connection_id=#{c}")
    long count(@Param("w") Long w,@Param("c") Long c);
    @Select("SELECT * FROM github_webhook_deliveries WHERE workspace_id=#{w} AND connection_id=#{c} AND id=#{id} FOR UPDATE")
    GithubWebhookDelivery delivery(@Param("w") Long w,@Param("c") Long c,@Param("id") Long id);
    @Update("""
            UPDATE github_webhook_deliveries SET status=#{status},attempts=#{attempts},run_id=#{runId},last_error=#{lastError},
                webhook_version=#{webhookVersion},next_attempt_at=#{nextAttemptAt} WHERE workspace_id=#{workspaceId} AND id=#{id}
            """)
    int update(GithubWebhookDelivery delivery);
    @Select("""
            SELECT h.* FROM integration_webhooks h WHERE EXISTS(SELECT 1 FROM github_webhook_deliveries d
            WHERE d.webhook_id=h.id AND ((d.status IN ('RECEIVED','RETRY') AND d.next_attempt_at<=NOW()) OR d.status='QUEUED'))
            ORDER BY h.id LIMIT 100
            """)
    List<GithubWebhook> pendingConnections();
    @Select("""
            SELECT * FROM github_webhook_deliveries WHERE workspace_id=#{w} AND connection_id=#{c}
                AND status IN ('RECEIVED','RETRY') AND next_attempt_at<=NOW() ORDER BY id LIMIT 100 FOR UPDATE
            """)
    List<GithubWebhookDelivery> pending(@Param("w") Long w,@Param("c") Long c);
    @Select("""
            SELECT d.*,r.status AS run_status,t.status AS target_status,t.last_error AS target_error
            FROM github_webhook_deliveries d JOIN integration_sync_runs r ON r.workspace_id=d.workspace_id AND r.id=d.run_id
            LEFT JOIN integration_issue_targets t ON t.workspace_id=d.workspace_id AND t.run_id=d.run_id AND t.source_id=d.source_id
            WHERE d.workspace_id=#{w} AND d.connection_id=#{c} AND d.status='QUEUED'
              AND r.status NOT IN ('QUEUED','RUNNING') ORDER BY d.id LIMIT 100 FOR UPDATE
            """)
    List<GithubWebhookDelivery> settled(@Param("w") Long w,@Param("c") Long c);
    @Update("""
            UPDATE integration_sync_runs SET status='CANCELLED',last_error='Webhook 配置已变化',finished_at=NOW()
            WHERE workspace_id=#{w} AND connection_id=#{c} AND kind='ISSUE_REFRESH' AND status IN ('QUEUED','RUNNING')
            """)
    int cancelRuns(@Param("w") Long w,@Param("c") Long c);
    @Update("""
            UPDATE github_webhook_deliveries SET status='CANCELLED',last_error='Webhook 配置或连接权限已变化'
            WHERE workspace_id=#{w} AND connection_id=#{c} AND status IN ('RECEIVED','RETRY','QUEUED')
            """)
    int cancelDeliveries(@Param("w") Long w,@Param("c") Long c);
    @Insert("""
            INSERT INTO integration_issue_targets(workspace_id,run_id,source_id,project_id,link_token)
            VALUES(#{w},#{run},#{source.id},#{source.projectId},#{source.linkToken}) ON DUPLICATE KEY UPDATE source_id=source_id
            """)
    int target(@Param("w") Long w,@Param("run") Long run,@Param("source") IssueSource source);
    @Select("""
            SELECT s.*,t.project_id,t.link_token FROM integration_issue_targets t
            JOIN external_issue_sources s ON s.workspace_id=t.workspace_id AND s.id=t.source_id
            WHERE t.workspace_id=#{w} AND t.run_id=#{run} AND t.status='WAITING' ORDER BY s.id
            """)
    List<IssueSource> targets(@Param("w") Long w,@Param("run") Long run);
    @Update("""
            UPDATE integration_issue_targets SET status=#{status},last_error=#{error}
            WHERE workspace_id=#{w} AND run_id=#{run} AND source_id=#{source}
            """)
    int completeTarget(@Param("w") Long w,@Param("run") Long run,@Param("source") Long source,@Param("status") String status,@Param("error") String error);
}

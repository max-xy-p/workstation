package com.mike.workstation.mapper;

import com.mike.workstation.domain.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 所有外部数据读写均携带 Workspace；连接行锁串行化配置与同步提交。 */
@Mapper
public interface IntegrationMapper {
    @Select("""
            SELECT * FROM integration_connections WHERE workspace_id=#{w} AND id=#{id} AND deleted=FALSE
            """)
    IntegrationConnection connection(@Param("w") Long w, @Param("id") Long id);

    @Select("""
            SELECT * FROM integration_connections WHERE workspace_id=#{w} AND id=#{id} FOR UPDATE
            """)
    IntegrationConnection lockConnection(@Param("w") Long w, @Param("id") Long id);

    @Select("""
            SELECT * FROM integration_connections WHERE workspace_id=#{w} AND deleted=FALSE ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<IntegrationConnection> connections(@Param("w") Long w, @Param("offset") long offset, @Param("size") int size);

    @Select("""
            SELECT COUNT(*) FROM integration_connections WHERE workspace_id=#{w} AND deleted=FALSE
            """)
    long countConnections(Long w);

    @Insert("""
            INSERT INTO integration_connections
            (workspace_id,name,provider,base_url,account,credential,key_version,scope,include_forks,include_archived,auto_import,schedule_minutes,enabled,version,owner_subject,next_sync_at)
            VALUES (#{workspaceId},#{name},#{provider},#{baseUrl},#{account},#{credential},#{keyVersion},#{scope},#{includeForks},#{includeArchived},#{autoImport},#{scheduleMinutes},#{enabled},#{version},#{ownerSubject},#{nextSyncAt})
            """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertConnection(IntegrationConnection c);

    @Update("""
            UPDATE integration_connections SET name=#{name},account=#{account},credential=#{credential},key_version=#{keyVersion},
            scope=#{scope},include_forks=#{includeForks},include_archived=#{includeArchived},auto_import=#{autoImport},schedule_minutes=#{scheduleMinutes},
            enabled=#{enabled},version=#{version},owner_subject=#{ownerSubject},last_error=#{lastError},next_sync_at=#{nextSyncAt}
            WHERE workspace_id=#{workspaceId} AND id=#{id} AND deleted=FALSE
            """)
    int updateConnection(IntegrationConnection c);

    @Update("""
            UPDATE integration_connections SET enabled=FALSE,deleted=TRUE,credential=NULL,version=version+1 WHERE workspace_id=#{w} AND id=#{id}
            """)
    int deleteConnection(@Param("w") Long w,@Param("id") Long id);

    @Update("""
            UPDATE external_repositories SET connection_id=NULL,last_error='数据源已断开' WHERE workspace_id=#{w} AND connection_id=#{id}
            """)
    int detachConnection(@Param("w") Long w,@Param("id") Long id);

    @Select("""
            SELECT r.*, p.project_id,
                (SELECT IF(s.available,s.open_count,NULL) FROM external_development_snapshots s
                    WHERE s.workspace_id=r.workspace_id AND s.repository_id=r.id AND s.kind='ISSUE') AS collected_open_issues,
                (SELECT IF(s.available,s.open_count,NULL) FROM external_development_snapshots s
                    WHERE s.workspace_id=r.workspace_id AND s.repository_id=r.id AND s.kind='CHANGE_REQUEST') AS collected_open_change_requests,
                (SELECT IF(s.available,(SELECT d.status FROM external_development_items d
                    WHERE d.workspace_id=r.workspace_id AND d.repository_id=r.id AND d.kind='PIPELINE'
                    ORDER BY d.source_updated_at DESC,d.id DESC LIMIT 1),NULL) FROM external_development_snapshots s
                    WHERE s.workspace_id=r.workspace_id AND s.repository_id=r.id AND s.kind='PIPELINE') AS latest_pipeline_status
                FROM external_repositories r LEFT JOIN project_repositories p ON p.workspace_id=r.workspace_id AND p.repository_id=r.id WHERE r.workspace_id=#{w} AND r.id=#{id}
            """)
    ExternalRepository repository(@Param("w") Long w,@Param("id") Long id);

    @Select("""
            SELECT id FROM external_repositories WHERE workspace_id=#{w} AND id=#{id} FOR UPDATE
            """)
    Long lockRepository(@Param("w") Long w,@Param("id") Long id);

    @Select("""
            <script>SELECT r.*, p.project_id,
                (SELECT IF(s.available,s.open_count,NULL) FROM external_development_snapshots s
                    WHERE s.workspace_id=r.workspace_id AND s.repository_id=r.id AND s.kind='ISSUE') AS collected_open_issues,
                (SELECT IF(s.available,s.open_count,NULL) FROM external_development_snapshots s
                    WHERE s.workspace_id=r.workspace_id AND s.repository_id=r.id AND s.kind='CHANGE_REQUEST') AS collected_open_change_requests,
                (SELECT IF(s.available,(SELECT d.status FROM external_development_items d
                    WHERE d.workspace_id=r.workspace_id AND d.repository_id=r.id AND d.kind='PIPELINE'
                    ORDER BY d.source_updated_at DESC,d.id DESC LIMIT 1),NULL) FROM external_development_snapshots s
                    WHERE s.workspace_id=r.workspace_id AND s.repository_id=r.id AND s.kind='PIPELINE') AS latest_pipeline_status
                FROM external_repositories r LEFT JOIN project_repositories p ON p.workspace_id=r.workspace_id AND p.repository_id=r.id <where>r.workspace_id=#{w}
            <if test="connectionId != null">AND r.connection_id=#{connectionId}</if>
            <if test="projectId != null and projectId > 0">AND p.project_id=#{projectId}</if>
            <if test="projectId != null and projectId == -1">AND p.project_id IS NOT NULL</if>
            </where> ORDER BY r.id LIMIT #{size} OFFSET #{offset}</script>
            """)
    List<ExternalRepository> repositories(@Param("w") Long w,@Param("connectionId") Long connectionId,@Param("projectId") Long projectId,@Param("offset") long offset,@Param("size") int size);

    @Select("""
            <script>SELECT COUNT(*) FROM external_repositories r LEFT JOIN project_repositories p ON p.workspace_id=r.workspace_id AND p.repository_id=r.id <where>r.workspace_id=#{w}
            <if test="connectionId != null">AND r.connection_id=#{connectionId}</if>
            <if test="projectId != null and projectId > 0">AND p.project_id=#{projectId}</if>
            <if test="projectId != null and projectId == -1">AND p.project_id IS NOT NULL</if>
            </where></script>
            """)
    long countRepositories(@Param("w") Long w,@Param("connectionId") Long connectionId,@Param("projectId") Long projectId);

    @Select("""
            SELECT * FROM external_repositories WHERE workspace_id=#{workspaceId} AND provider=#{provider} AND base_url=#{baseUrl} AND external_id=#{externalId}
            """)
    ExternalRepository source(ExternalRepository r);

    @Insert("""
            INSERT INTO external_repositories
            (workspace_id,connection_id,provider,base_url,external_id,namespace,name,url,description,default_branch,language,visibility,archived,fork,last_pushed_at,last_synced_at)
            VALUES (#{workspaceId},#{connectionId},#{provider},#{baseUrl},#{externalId},#{namespace},#{name},#{url},#{description},#{defaultBranch},#{language},#{visibility},#{archived},#{fork},#{lastPushedAt},#{lastSyncedAt})
            """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertRepository(ExternalRepository r);

    @Update("""
            UPDATE external_repositories SET connection_id=#{connectionId},namespace=#{namespace},name=#{name},url=#{url},description=#{description},
            default_branch=#{defaultBranch},language=#{language},visibility=#{visibility},archived=#{archived},fork=#{fork},last_pushed_at=#{lastPushedAt},
            last_synced_at=#{lastSyncedAt},last_error=NULL WHERE workspace_id=#{workspaceId} AND id=#{id}
            """)
    int updateRepository(ExternalRepository r);

    @Update("""
            UPDATE external_repositories SET last_error=#{error} WHERE workspace_id=#{w} AND id=#{id}
            """)
    int repositoryError(@Param("w") Long w,@Param("id") Long id,@Param("error") String error);

    @Insert("""
            INSERT INTO project_repositories (workspace_id,project_id,repository_id) VALUES (#{w},#{projectId},#{repositoryId})
            """)
    int bind(@Param("w") Long w,@Param("projectId") Long projectId,@Param("repositoryId") Long repositoryId);

    @Delete("""
            DELETE FROM project_repositories WHERE workspace_id=#{w} AND project_id=#{projectId} AND repository_id=#{repositoryId}
            """)
    int unbind(@Param("w") Long w,@Param("projectId") Long projectId,@Param("repositoryId") Long repositoryId);

    @Update("UPDATE external_repositories r JOIN project_repositories p ON p.workspace_id=r.workspace_id AND p.repository_id=r.id SET r.auto_import_suppressed=TRUE WHERE p.workspace_id=#{w} AND p.project_id=#{project}")
    int suppressProject(@Param("w") Long w,@Param("project") Long project);

    @Update("UPDATE external_repositories r JOIN project_repositories p ON p.workspace_id=r.workspace_id AND p.repository_id=r.id SET r.auto_import_suppressed=TRUE WHERE p.workspace_id=#{w} AND p.project_id=#{project} AND r.id=#{repository}")
    int suppressRepository(@Param("w") Long w,@Param("project") Long project,@Param("repository") Long repository);

    @Insert("""
            INSERT INTO integration_sync_runs (workspace_id,connection_id,kind,status,request_key,request_hash,actor_subject,config_version,created_at)
            VALUES (#{workspaceId},#{connectionId},#{kind},#{status},#{requestKey},#{requestHash},#{actorSubject},#{configVersion},#{createdAt})
            """)
    @Options(useGeneratedKeys=true,keyProperty="id")
    int insertRun(IntegrationRun r);

    @Select("""
            SELECT * FROM integration_sync_runs WHERE workspace_id=#{w} AND connection_id=#{c} AND actor_subject=#{actor} AND request_key=#{key}
            """)
    IntegrationRun request(@Param("w") Long w,@Param("c") Long c,@Param("actor") String actor,@Param("key") String key);

    @Select("""
            SELECT * FROM integration_sync_runs WHERE workspace_id=#{w} AND connection_id=#{c} AND status IN ('QUEUED','RUNNING') ORDER BY id LIMIT 1
            """)
    IntegrationRun active(@Param("w") Long w,@Param("c") Long c);

    @Select("""
            SELECT * FROM integration_sync_runs WHERE workspace_id=#{w} AND connection_id=#{c} AND id=#{id}
            """)
    IntegrationRun run(@Param("w") Long w,@Param("c") Long c,@Param("id") Long id);

    @Select("""
            SELECT * FROM integration_sync_runs WHERE workspace_id=#{w} AND connection_id=#{c} ORDER BY id DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<IntegrationRun> runs(@Param("w") Long w,@Param("c") Long c,@Param("offset") long offset,@Param("size") int size);

    @Select("""
            SELECT COUNT(*) FROM integration_sync_runs WHERE workspace_id=#{w} AND connection_id=#{c}
            """)
    long countRuns(@Param("w") Long w,@Param("c") Long c);

    @Insert("""
            INSERT INTO integration_sync_items (workspace_id,run_id,repository_id,project_id,project_status) VALUES (#{workspaceId},#{runId},#{repositoryId},#{projectId},#{projectStatus})
            """)
    int insertItem(IntegrationRunItem i);

    @Select("""
            SELECT * FROM integration_sync_items WHERE workspace_id=#{w} AND run_id=#{run} ORDER BY id LIMIT #{size} OFFSET #{offset}
            """)
    List<IntegrationRunItem> items(@Param("w") Long w,@Param("run") Long run,@Param("offset") long offset,@Param("size") int size);

    @Select("""
            SELECT COUNT(*) FROM integration_sync_items WHERE workspace_id=#{w} AND run_id=#{run}
            """)
    long countItems(@Param("w") Long w,@Param("run") Long run);

    @Update("""
            UPDATE integration_sync_items SET status=#{status},project_id=#{projectId},last_error=#{lastError} WHERE workspace_id=#{workspaceId} AND id=#{id}
            """)
    int updateItem(IntegrationRunItem i);

    @Select("""
            SELECT * FROM integration_sync_runs r WHERE (r.status='QUEUED' OR (r.status='RUNNING' AND r.lease_until < NOW()))
            AND NOT EXISTS (SELECT 1 FROM integration_sync_runs a WHERE a.connection_id=r.connection_id AND a.status='RUNNING' AND a.lease_until >= NOW())
            ORDER BY r.id LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    IntegrationRun nextRun();

    @Update("""
            UPDATE integration_sync_runs SET status='RUNNING',lease_token=#{leaseToken},lease_until=#{leaseUntil} WHERE workspace_id=#{workspaceId} AND id=#{id}
            """)
    int claim(IntegrationRun r);

    @Update("""
            UPDATE integration_sync_runs SET issue_cursor=#{issueCursor},issue_upper_bound=#{issueUpperBound},lease_until=#{leaseUntil},processed=#{processed},failed=#{failed},last_error=#{lastError},status=#{status},finished_at=#{finishedAt}
            WHERE workspace_id=#{workspaceId} AND id=#{id} AND lease_token=#{leaseToken} AND status='RUNNING' AND lease_until >= NOW()
            """)
    int updateRun(IntegrationRun r);

    @Select("""
            SELECT * FROM integration_sync_runs WHERE workspace_id=#{workspaceId} AND id=#{id} AND status='RUNNING' AND lease_token=#{leaseToken} AND lease_until >= NOW() FOR UPDATE
            """)
    IntegrationRun lockRun(IntegrationRun r);

    @Update("""
            UPDATE integration_sync_runs SET status='CANCELLED',last_error='连接配置或权限已变化',finished_at=NOW() WHERE workspace_id=#{w} AND connection_id=#{c} AND status IN ('QUEUED','RUNNING')
            """)
    int cancel(@Param("w") Long w,@Param("c") Long c);

    @Select("""
            SELECT * FROM integration_connections WHERE enabled=TRUE AND deleted=FALSE AND schedule_minutes>0 AND next_sync_at<=NOW() ORDER BY next_sync_at LIMIT 20
            """)
    List<IntegrationConnection> dueConnections();

    @Update("""
            UPDATE integration_connections SET last_synced_at=IF(#{error} IS NULL,#{now},last_synced_at),last_error=#{error},next_sync_at=DATE_ADD(#{now},INTERVAL schedule_minutes MINUTE) WHERE workspace_id=#{w} AND id=#{id}
            """)
    int completed(@Param("w") Long w,@Param("id") Long id,@Param("now") LocalDateTime now,@Param("error") String error);

    @Delete("""
            DELETE FROM external_development_items WHERE workspace_id=#{w} AND repository_id=#{r} AND kind=#{kind}
            """)
    int clearDevelopment(@Param("w") Long w,@Param("r") Long r,@Param("kind") String kind);

    @Insert("""
            INSERT INTO external_development_items (workspace_id,repository_id,kind,external_id,title,url,status,raw_status,author,branch,source_updated_at,issue_number,state_reason)
            VALUES (#{workspaceId},#{repositoryId},#{kind},#{externalId},#{title},#{url},#{status},#{rawStatus},#{author},#{branch},#{sourceUpdatedAt},#{issueNumber},#{stateReason})
            """)
    int insertDevelopment(DevelopmentItem i);

    @Insert("""
            INSERT INTO external_development_snapshots (workspace_id,repository_id,kind,available,truncated,message,since,synced_at,collected,open_count)
            VALUES (#{workspaceId},#{repositoryId},#{kind},#{available},#{truncated},#{message},#{since},#{syncedAt},#{collected},#{openCount})
            ON DUPLICATE KEY UPDATE available=VALUES(available),truncated=VALUES(truncated),message=VALUES(message),since=VALUES(since),synced_at=VALUES(synced_at),collected=VALUES(collected),open_count=VALUES(open_count)
            """)
    int snapshot(DevelopmentSnapshot s);

    @Select("""
            SELECT s.* FROM external_development_snapshots s JOIN project_repositories p ON p.workspace_id=s.workspace_id AND p.repository_id=s.repository_id
            WHERE s.workspace_id=#{w} AND p.project_id=#{projectId}
            """)
    List<DevelopmentSnapshot> snapshots(@Param("w") Long w,@Param("projectId") Long projectId);

    @Select("""
            SELECT d.*, l.task_id AS linked_task_id FROM external_development_items d
            JOIN external_repositories r ON r.workspace_id=d.workspace_id AND r.id=d.repository_id
            JOIN project_repositories p ON p.workspace_id=d.workspace_id AND p.repository_id=d.repository_id
            LEFT JOIN external_issue_sources s ON s.workspace_id=d.workspace_id AND s.provider=r.provider
                AND s.base_url=r.base_url AND s.external_id=d.external_id AND d.kind='ISSUE'
            LEFT JOIN task_issue_links l ON l.workspace_id=s.workspace_id AND l.source_id=s.id
            WHERE d.workspace_id=#{w} AND p.project_id=#{projectId} AND d.kind=#{kind}
            ORDER BY d.source_updated_at DESC,d.id DESC LIMIT #{size} OFFSET #{offset}
            """)
    List<DevelopmentItem> development(@Param("w") Long w,@Param("projectId") Long projectId,@Param("kind") String kind,@Param("offset") long offset,@Param("size") int size);

    @Select("""
            SELECT COUNT(*) FROM external_development_items d JOIN project_repositories p ON p.workspace_id=d.workspace_id AND p.repository_id=d.repository_id WHERE d.workspace_id=#{w} AND p.project_id=#{projectId} AND d.kind=#{kind}
            """)
    long countDevelopment(@Param("w") Long w,@Param("projectId") Long projectId,@Param("kind") String kind);
}

package com.mike.workstation.mapper;

import com.mike.workstation.domain.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 来源身份、关联和跟踪进度均持久化在 MySQL，并按 Workspace 隔离。 */
@Mapper
public interface IssueSourceMapper {
    String LINKED = """
            SELECT s.*, l.task_id,l.link_token,t.project_id,CONCAT(r.namespace,'/',r.name) AS repository_name,
                CASE WHEN p.project_id IS NULL OR p.project_id!=t.project_id OR c.id IS NULL
                      OR c.deleted=TRUE OR c.enabled=FALSE OR m.role IS NULL OR m.role NOT IN ('OWNER','ADMIN')
                     THEN 'STOPPED'
                     WHEN s.last_error IS NOT NULL THEN 'ERROR'
                     WHEN s.last_attempt_at IS NULL THEN 'WAITING' ELSE 'ACTIVE' END AS tracking_status
            FROM external_issue_sources s
            JOIN task_issue_links l ON l.workspace_id=s.workspace_id AND l.source_id=s.id
            JOIN tasks t ON t.workspace_id=l.workspace_id AND t.id=l.task_id
            JOIN external_repositories r ON r.workspace_id=s.workspace_id AND r.id=s.repository_id
            LEFT JOIN project_repositories p ON p.workspace_id=r.workspace_id AND p.repository_id=r.id
            LEFT JOIN integration_connections c ON c.workspace_id=r.workspace_id AND c.id=r.connection_id
            LEFT JOIN workspace_members m ON m.workspace_id=c.workspace_id AND m.principal_subject=c.owner_subject
            """;

    @Select(LINKED+" WHERE s.workspace_id=#{w} AND r.connection_id=#{c} AND r.external_id=#{repo} AND s.external_id=#{issue} AND s.provider='GITHUB' AND p.project_id=t.project_id")
    IssueSource external(@Param("w") Long w,@Param("c") Long c,@Param("repo") String repo,@Param("issue") String issue);

    @Select(LINKED+" WHERE s.workspace_id=#{w} AND s.id=#{id}")
    IssueSource tracked(@Param("w") Long w,@Param("id") Long id);

    @Select("SELECT id FROM workspaces WHERE id=#{w} FOR UPDATE")
    Long lockWorkspace(Long w);

    @Select("SELECT * FROM external_development_items WHERE workspace_id=#{w} AND id=#{id} FOR UPDATE")
    DevelopmentItem candidate(@Param("w") Long w,@Param("id") Long id);

    @Insert("""
            INSERT INTO external_issue_sources
            (workspace_id,repository_id,provider,base_url,external_id,issue_number,title,url,status,state_reason,source_updated_at,last_success_at)
            VALUES (#{workspaceId},#{repositoryId},#{provider},#{baseUrl},#{externalId},#{issueNumber},#{title},#{url},#{status},#{stateReason},#{sourceUpdatedAt},#{lastSuccessAt})
            ON DUPLICATE KEY UPDATE id=id
            """)
    int ensure(IssueSource s);

    @Select("""
            SELECT * FROM external_issue_sources WHERE workspace_id=#{workspaceId} AND provider=#{provider}
                AND base_url=#{baseUrl} AND external_id=#{externalId} FOR UPDATE
            """)
    IssueSource identity(IssueSource s);

    @Select("SELECT * FROM external_issue_sources WHERE workspace_id=#{w} AND id=#{id} FOR UPDATE")
    IssueSource lock(@Param("w") Long w,@Param("id") Long id);

    @Select(LINKED+" WHERE s.workspace_id=#{w} AND l.task_id=#{task}")
    IssueSource forTask(@Param("w") Long w,@Param("task") Long task);

    @Select("SELECT task_id FROM task_issue_links WHERE workspace_id=#{w} AND source_id=#{source} FOR UPDATE")
    Long linkedTask(@Param("w") Long w,@Param("source") Long source);

    @Insert("""
            INSERT INTO task_issue_links (workspace_id,task_id,source_id,link_token,actor_subject,created_at)
            VALUES (#{w},#{task},#{source},UUID(),#{actor},NOW())
            """)
    int link(@Param("w") Long w,@Param("task") Long task,@Param("source") Long source,@Param("actor") String actor);

    @Delete("DELETE FROM task_issue_links WHERE workspace_id=#{w} AND task_id=#{task}")
    int unlink(@Param("w") Long w,@Param("task") Long task);

    @Select("<script>"+LINKED+" WHERE s.workspace_id=#{w} <if test='project != null'>AND t.project_id=#{project}</if> ORDER BY s.id LIMIT #{size} OFFSET #{offset}</script>")
    List<IssueSource> list(@Param("w") Long w,@Param("project") Long project,@Param("offset") long offset,@Param("size") int size);

    @Select("""
            <script>SELECT COUNT(*) FROM task_issue_links l JOIN tasks t ON t.workspace_id=l.workspace_id AND t.id=l.task_id
            WHERE l.workspace_id=#{w} <if test='project != null'>AND t.project_id=#{project}</if></script>
            """)
    long count(@Param("w") Long w,@Param("project") Long project);

    @Select("SELECT COALESCE(MAX(id),0) FROM external_issue_sources WHERE workspace_id=#{w}")
    long upperBound(Long w);

    @Select(LINKED+"""
             WHERE s.workspace_id=#{w} AND r.connection_id=#{connection} AND p.project_id=t.project_id
                AND s.id>#{cursor} AND s.id<=#{upper} ORDER BY s.id LIMIT 50
            """)
    List<IssueSource> pending(@Param("w") Long w,@Param("connection") Long connection,@Param("cursor") long cursor,@Param("upper") long upper);

    @Select("SELECT source_id FROM task_issue_links WHERE workspace_id=#{w} AND task_id=#{task} FOR UPDATE")
    Long sourceForTask(@Param("w") Long w,@Param("task") Long task);

    @Select("SELECT t.project_id FROM tasks t JOIN task_issue_links l ON l.workspace_id=t.workspace_id AND l.task_id=t.id WHERE t.workspace_id=#{w} AND t.id=#{task} FOR UPDATE")
    Long linkedProject(@Param("w") Long w,@Param("task") Long task);

    @Select("SELECT link_token FROM task_issue_links WHERE workspace_id=#{w} AND source_id=#{source} FOR UPDATE")
    String linkToken(@Param("w") Long w,@Param("source") Long source);

    @Select("SELECT project_id FROM project_repositories WHERE workspace_id=#{w} AND repository_id=#{repo} FOR UPDATE")
    Long boundProject(@Param("w") Long w,@Param("repo") Long repo);

    @Update("""
            UPDATE external_issue_sources SET title=#{title},url=#{url},status=#{status},state_reason=#{stateReason},
                source_updated_at=#{sourceUpdatedAt},last_attempt_at=#{lastAttemptAt},last_success_at=#{lastSuccessAt},last_error=#{lastError}
            WHERE workspace_id=#{workspaceId} AND id=#{id}
            """)
    int update(IssueSource s);
}

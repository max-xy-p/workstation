package com.mike.workstation.mapper;

import com.mike.workstation.domain.Task;
import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务表数据访问接口。
 *
 * <p>查询时会关联 projects 表，给前端返回任务所属项目名称。
 */
@Mapper
public interface TaskMapper {
    /** 按标题、项目、状态和优先级组合筛选任务。 */
    @Select(
            """
            <script>
            SELECT t.id, t.workspace_id, t.title, t.description, t.project_id, p.name AS project_name,
                   t.status, t.priority, t.due_date, t.evidence_note_id, n.title AS evidence_note_title,
                   t.created_at, t.updated_at, t.version
            FROM tasks t
            JOIN projects p ON p.workspace_id = t.workspace_id AND p.id = t.project_id
            LEFT JOIN notes n ON n.workspace_id = t.workspace_id AND n.id = t.evidence_note_id
            <where>
                t.workspace_id = #{workspaceId}
                <if test="keyword != null and keyword != ''">
                    AND t.title LIKE CONCAT('%', #{keyword}, '%')
                </if>
                <if test="projectId != null">
                    AND t.project_id = #{projectId}
                </if>
                <if test="status != null">
                    AND t.status = #{status}
                </if>
                <if test="priority != null">
                    AND t.priority = #{priority}
                </if>
            </where>
            ORDER BY t.updated_at DESC, t.id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Task> listPage(
            @Param("workspaceId") Long workspaceId,
            @Param("keyword") String keyword,
            @Param("projectId") Long projectId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority,
            @Param("offset") long offset,
            @Param("size") int size);

    @Select(
            """
            <script>
            SELECT COUNT(*) FROM tasks t
            <where>
                t.workspace_id = #{workspaceId}
                <if test="keyword != null and keyword != ''">AND t.title LIKE CONCAT('%', #{keyword}, '%')</if>
                <if test="projectId != null">AND t.project_id = #{projectId}</if>
                <if test="status != null">AND t.status = #{status}</if>
                <if test="priority != null">AND t.priority = #{priority}</if>
            </where>
            </script>
            """)
    long count(
            @Param("workspaceId") Long workspaceId,
            @Param("keyword") String keyword,
            @Param("projectId") Long projectId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority);

    @Select(
            """
            SELECT t.id, t.workspace_id, t.title, t.description, t.project_id, p.name AS project_name,
                   t.status, t.priority, t.due_date, t.evidence_note_id, n.title AS evidence_note_title,
                   t.created_at, t.updated_at, t.version
            FROM tasks t
            JOIN projects p ON p.workspace_id = t.workspace_id AND p.id = t.project_id
            LEFT JOIN notes n ON n.workspace_id = t.workspace_id AND n.id = t.evidence_note_id
            WHERE t.workspace_id = #{workspaceId} AND t.project_id = #{projectId}
            ORDER BY t.updated_at DESC, t.id DESC
            """)
    List<Task> listByProjectId(
            @Param("workspaceId") Long workspaceId, @Param("projectId") Long projectId);

    /** 根据主键查询任务详情。 */
    @Select(
            """
            SELECT t.id, t.workspace_id, t.title, t.description, t.project_id, p.name AS project_name,
                   t.status, t.priority, t.due_date, t.evidence_note_id, n.title AS evidence_note_title,
                   t.created_at, t.updated_at, t.version
            FROM tasks t
            JOIN projects p ON p.workspace_id = t.workspace_id AND p.id = t.project_id
            LEFT JOIN notes n ON n.workspace_id = t.workspace_id AND n.id = t.evidence_note_id
            WHERE t.workspace_id = #{workspaceId} AND t.id = #{id}
            """)
    Task findById(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    @Select(
            """
            SELECT t.id, t.workspace_id, t.title, t.description, t.project_id, p.name AS project_name,
                   t.status, t.priority, t.due_date, t.evidence_note_id, n.title AS evidence_note_title,
                   t.created_at, t.updated_at, t.version
            FROM tasks t
            JOIN projects p ON p.workspace_id = t.workspace_id AND p.id = t.project_id
            LEFT JOIN notes n ON n.workspace_id = t.workspace_id AND n.id = t.evidence_note_id
            WHERE t.workspace_id = #{workspaceId} AND t.id = #{id} FOR UPDATE
            """)
    Task lockById(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    /** 插入任务，并回填自增主键到 task.id。 */
    @Insert(
            """
            INSERT INTO tasks
                (workspace_id, title, description, project_id, status, priority, due_date, evidence_note_id, created_at, updated_at)
            VALUES
                (#{workspaceId}, #{title}, #{description}, #{projectId}, #{status}, #{priority}, #{dueDate}, #{evidenceNoteId}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Task task);

    /** 更新任务完整信息。 */
    @Update(
            """
            UPDATE tasks
            SET version = version + 1, title = #{title},
                description = #{description},
                project_id = #{projectId},
                status = #{status},
                priority = #{priority},
                due_date = #{dueDate},
                evidence_note_id = #{evidenceNoteId},
                updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND id = #{id}
            """)
    int update(Task task);

    /** 更新任务看板状态和更新时间。 */
    @Update(
            """
            UPDATE tasks
            SET version = version + 1, status = #{status}, updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND id = #{id}
            """)
    int updateStatus(
            @Param("workspaceId") Long workspaceId,
            @Param("id") Long id,
            @Param("status") TaskStatus status,
            @Param("updatedAt") LocalDateTime updatedAt);

    /** 根据主键删除任务。 */
    @Delete("DELETE FROM tasks WHERE workspace_id = #{workspaceId} AND id = #{id}")
    int delete(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    @Update(
            """
            UPDATE tasks SET version = version + 1, evidence_note_id = NULL, updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND evidence_note_id = #{noteId}
            """)
    int clearEvidenceNote(
            @Param("workspaceId") Long workspaceId,
            @Param("noteId") Long noteId,
            @Param("updatedAt") LocalDateTime updatedAt);

    @Update(
            """
            UPDATE tasks SET version = version + 1, evidence_note_id = NULL, updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND evidence_note_id = #{noteId}
              AND (#{projectId} IS NULL OR project_id != #{projectId})
            """)
    int clearEvidenceOutsideProject(
            @Param("workspaceId") Long workspaceId,
            @Param("noteId") Long noteId,
            @Param("projectId") Long projectId,
            @Param("updatedAt") LocalDateTime updatedAt);

    /** 统计某项目下的任务数量，用于项目删除前校验。 */
    @Select(
            "SELECT COUNT(*) FROM tasks WHERE workspace_id = #{workspaceId} AND project_id = #{projectId}")
    long countByProjectId(
            @Param("workspaceId") Long workspaceId, @Param("projectId") Long projectId);

    /** 按任务状态统计数量，用于首页概览。 */
    @Select("SELECT COUNT(*) FROM tasks WHERE workspace_id = #{workspaceId} AND status = #{status}")
    long countByStatus(@Param("workspaceId") Long workspaceId, @Param("status") TaskStatus status);
}

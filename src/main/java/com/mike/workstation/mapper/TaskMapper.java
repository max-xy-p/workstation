package com.mike.workstation.mapper;

import com.mike.workstation.domain.Task;
import com.mike.workstation.domain.TaskPriority;
import com.mike.workstation.domain.TaskStatus;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
/**
 * 任务表数据访问接口。
 *
 * <p>查询时会关联 projects 表，给前端返回任务所属项目名称。</p>
 */
public interface TaskMapper {
    /**
     * 按标题、项目、状态和优先级组合筛选任务。
     */
    @Select("""
            <script>
            SELECT t.id, t.title, t.description, t.project_id, p.name AS project_name,
                   t.status, t.priority, t.due_date, t.created_at, t.updated_at
            FROM tasks t
            JOIN projects p ON p.id = t.project_id
            <where>
                <if test="keyword != null and keyword != ''">
                    t.title LIKE CONCAT('%', #{keyword}, '%')
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
            </script>
            """)
    List<Task> list(
            @Param("keyword") String keyword,
            @Param("projectId") Long projectId,
            @Param("status") TaskStatus status,
            @Param("priority") TaskPriority priority
    );

    /**
     * 根据主键查询任务详情。
     */
    @Select("""
            SELECT t.id, t.title, t.description, t.project_id, p.name AS project_name,
                   t.status, t.priority, t.due_date, t.created_at, t.updated_at
            FROM tasks t
            JOIN projects p ON p.id = t.project_id
            WHERE t.id = #{id}
            """)
    Task findById(Long id);

    /**
     * 插入任务，并回填自增主键到 task.id。
     */
    @Insert("""
            INSERT INTO tasks (title, description, project_id, status, priority, due_date, created_at, updated_at)
            VALUES (#{title}, #{description}, #{projectId}, #{status}, #{priority}, #{dueDate}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Task task);

    /**
     * 更新任务完整信息。
     */
    @Update("""
            UPDATE tasks
            SET title = #{title},
                description = #{description},
                project_id = #{projectId},
                status = #{status},
                priority = #{priority},
                due_date = #{dueDate},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(Task task);

    /**
     * 更新任务看板状态和更新时间。
     */
    @Update("""
            UPDATE tasks
            SET status = #{status}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateStatus(
            @Param("id") Long id,
            @Param("status") TaskStatus status,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );

    /**
     * 根据主键删除任务。
     */
    @Delete("DELETE FROM tasks WHERE id = #{id}")
    int delete(Long id);

    /**
     * 统计某项目下的任务数量，用于项目删除前校验。
     */
    @Select("SELECT COUNT(*) FROM tasks WHERE project_id = #{projectId}")
    long countByProjectId(Long projectId);

    /**
     * 按任务状态统计数量，用于首页概览。
     */
    @Select("SELECT COUNT(*) FROM tasks WHERE status = #{status}")
    long countByStatus(TaskStatus status);
}

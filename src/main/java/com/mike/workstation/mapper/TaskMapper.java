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
public interface TaskMapper {
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

    @Select("""
            SELECT t.id, t.title, t.description, t.project_id, p.name AS project_name,
                   t.status, t.priority, t.due_date, t.created_at, t.updated_at
            FROM tasks t
            JOIN projects p ON p.id = t.project_id
            WHERE t.id = #{id}
            """)
    Task findById(Long id);

    @Insert("""
            INSERT INTO tasks (title, description, project_id, status, priority, due_date, created_at, updated_at)
            VALUES (#{title}, #{description}, #{projectId}, #{status}, #{priority}, #{dueDate}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Task task);

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

    @Delete("DELETE FROM tasks WHERE id = #{id}")
    int delete(Long id);

    @Select("SELECT COUNT(*) FROM tasks WHERE project_id = #{projectId}")
    long countByProjectId(Long projectId);

    @Select("SELECT COUNT(*) FROM tasks WHERE status = #{status}")
    long countByStatus(TaskStatus status);
}

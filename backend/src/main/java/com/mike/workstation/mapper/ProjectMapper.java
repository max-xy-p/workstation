package com.mike.workstation.mapper;

import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.ProjectStatus;

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
 * 项目表数据访问接口。
 *
 * <p>只负责 projects 表的 SQL 操作，不承载业务删除规则。
 */
@Mapper
public interface ProjectMapper {
    /** 按名称关键字和状态查询项目列表。 */
    @Select(
            """
            <script>
            SELECT id, workspace_id, name, description, status, repository_url, homepage_url, tech_stack, created_at, updated_at
            FROM projects
            <where>
                workspace_id = #{workspaceId}
                <if test="keyword != null and keyword != ''">
                    AND name LIKE CONCAT('%', #{keyword}, '%')
                </if>
                <if test="status != null">
                    AND status = #{status}
                </if>
            </where>
            ORDER BY updated_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Project> listPage(
            @Param("workspaceId") Long workspaceId,
            @Param("keyword") String keyword,
            @Param("status") ProjectStatus status,
            @Param("offset") long offset,
            @Param("size") int size);

    @Select(
            """
            <script>
            SELECT COUNT(*) FROM projects
            <where>
                workspace_id = #{workspaceId}
                <if test="keyword != null and keyword != ''">AND name LIKE CONCAT('%', #{keyword}, '%')</if>
                <if test="status != null">AND status = #{status}</if>
            </where>
            </script>
            """)
    long count(
            @Param("workspaceId") Long workspaceId,
            @Param("keyword") String keyword,
            @Param("status") ProjectStatus status);

    /** 根据主键查询项目。 */
    @Select(
            """
            SELECT id, workspace_id, name, description, status, repository_url, homepage_url, tech_stack, run_guide, created_at, updated_at
            FROM projects
            WHERE workspace_id = #{workspaceId} AND id = #{id}
            """)
    Project findById(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    /** 插入项目，并回填自增主键到 project.id。 */
    @Insert(
            """
            INSERT INTO projects
                (workspace_id, name, description, status, repository_url, homepage_url, tech_stack, run_guide, created_at, updated_at)
            VALUES
                (#{workspaceId}, #{name}, #{description}, #{status}, #{repositoryUrl}, #{homepageUrl}, #{techStack}, #{runGuide}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Project project);

    /** 更新项目完整信息。 */
    @Update(
            """
            UPDATE projects
            SET name = #{name},
                description = #{description},
                status = #{status},
                repository_url = #{repositoryUrl},
                homepage_url = #{homepageUrl},
                tech_stack = #{techStack},
                run_guide = #{runGuide},
                updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND id = #{id}
            """)
    int update(Project project);

    /** 更新项目状态和更新时间。 */
    @Update(
            """
            UPDATE projects
            SET status = #{status}, updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND id = #{id}
            """)
    int updateStatus(
            @Param("workspaceId") Long workspaceId,
            @Param("id") Long id,
            @Param("status") ProjectStatus status,
            @Param("updatedAt") LocalDateTime updatedAt);

    /** 根据主键删除项目。 */
    @Delete("DELETE FROM projects WHERE workspace_id = #{workspaceId} AND id = #{id}")
    int delete(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    /** 按项目状态统计数量。 */
    @Select(
            "SELECT COUNT(*) FROM projects WHERE workspace_id = #{workspaceId} AND status = #{status}")
    long countByStatus(
            @Param("workspaceId") Long workspaceId, @Param("status") ProjectStatus status);

    /** 查询最近更新的项目，用于首页概览。 */
    @Select(
            """
            SELECT id, workspace_id, name, description, status, repository_url, homepage_url, tech_stack, created_at, updated_at
            FROM projects
            WHERE workspace_id = #{workspaceId}
            ORDER BY updated_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<Project> recent(@Param("workspaceId") Long workspaceId, @Param("limit") int limit);
}

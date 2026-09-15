package com.mike.workstation.mapper;

import com.mike.workstation.domain.Project;
import com.mike.workstation.domain.ProjectStatus;
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
 * 项目表数据访问接口。
 *
 * <p>只负责 projects 表的 SQL 操作，不承载业务删除规则。</p>
 */
public interface ProjectMapper {
    /**
     * 按名称关键字和状态查询项目列表。
     */
    @Select("""
            <script>
            SELECT id, name, description, status, repository_url, tech_stack, created_at, updated_at
            FROM projects
            <where>
                <if test="keyword != null and keyword != ''">
                    name LIKE CONCAT('%', #{keyword}, '%')
                </if>
                <if test="status != null">
                    AND status = #{status}
                </if>
            </where>
            ORDER BY updated_at DESC, id DESC
            </script>
            """)
    List<Project> list(@Param("keyword") String keyword, @Param("status") ProjectStatus status);

    /**
     * 根据主键查询项目。
     */
    @Select("""
            SELECT id, name, description, status, repository_url, tech_stack, run_guide, created_at, updated_at
            FROM projects
            WHERE id = #{id}
            """)
    Project findById(Long id);

    /**
     * 插入项目，并回填自增主键到 project.id。
     */
    @Insert("""
            INSERT INTO projects (name, description, status, repository_url, tech_stack, run_guide, created_at, updated_at)
            VALUES (#{name}, #{description}, #{status}, #{repositoryUrl}, #{techStack}, #{runGuide}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Project project);

    /**
     * 更新项目完整信息。
     */
    @Update("""
            UPDATE projects
            SET name = #{name},
                description = #{description},
                status = #{status},
                repository_url = #{repositoryUrl},
                tech_stack = #{techStack},
                run_guide = #{runGuide},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(Project project);

    /**
     * 更新项目状态和更新时间。
     */
    @Update("""
            UPDATE projects
            SET status = #{status}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int updateStatus(
            @Param("id") Long id,
            @Param("status") ProjectStatus status,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );

    /**
     * 根据主键删除项目。
     */
    @Delete("DELETE FROM projects WHERE id = #{id}")
    int delete(Long id);

    /**
     * 按项目状态统计数量。
     */
    @Select("SELECT COUNT(*) FROM projects WHERE status = #{status}")
    long countByStatus(ProjectStatus status);

    /**
     * 查询最近更新的项目，用于首页概览。
     */
    @Select("""
            SELECT id, name, description, status, repository_url, tech_stack, created_at, updated_at
            FROM projects
            ORDER BY updated_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<Project> recent(int limit);
}

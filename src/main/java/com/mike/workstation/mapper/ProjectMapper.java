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
public interface ProjectMapper {
    @Select("""
            <script>
            SELECT id, name, description, status, repository_url, tech_stack, run_guide, created_at, updated_at
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

    @Select("""
            SELECT id, name, description, status, repository_url, tech_stack, run_guide, created_at, updated_at
            FROM projects
            WHERE id = #{id}
            """)
    Project findById(Long id);

    @Insert("""
            INSERT INTO projects (name, description, status, repository_url, tech_stack, run_guide, created_at, updated_at)
            VALUES (#{name}, #{description}, #{status}, #{repositoryUrl}, #{techStack}, #{runGuide}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Project project);

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

    @Delete("DELETE FROM projects WHERE id = #{id}")
    int delete(Long id);

    @Select("SELECT COUNT(*) FROM projects WHERE status = #{status}")
    long countByStatus(ProjectStatus status);

    @Select("""
            SELECT id, name, description, status, repository_url, tech_stack, run_guide, created_at, updated_at
            FROM projects
            ORDER BY updated_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<Project> recent(int limit);
}

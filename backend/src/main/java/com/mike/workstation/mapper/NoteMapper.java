package com.mike.workstation.mapper;

import com.mike.workstation.domain.Note;
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
 * 笔记表数据访问接口。
 *
 * <p>负责 notes 表查询和写入；标签关联由 {@link TagMapper} 维护。</p>
 */
public interface NoteMapper {
    /**
     * 按标题、正文、文件夹、标签和项目组合筛选笔记。
     */
    @Select("""
            <script>
            SELECT n.id, n.workspace_id, n.title, n.folder_id, f.name AS folder_name, n.version,
                   n.project_id, p.name AS project_name, n.created_at, n.updated_at,
                   CASE WHEN #{keyword} IS NULL OR #{keyword} = '' THEN NULL
                        ELSE SUBSTRING(n.content, GREATEST(1, LOCATE(#{keyword}, n.content) - 60), 200)
                   END AS excerpt
            FROM notes n
            LEFT JOIN note_folders f ON f.workspace_id = n.workspace_id AND f.id = n.folder_id
            LEFT JOIN projects p ON p.workspace_id = n.workspace_id AND p.id = n.project_id
            <where>
                n.workspace_id = #{workspaceId}
                AND n.deleted_at IS NULL
                <if test="keyword != null and keyword != ''">
                    AND (
                        n.title LIKE CONCAT('%', #{keyword}, '%')
                        OR n.content LIKE CONCAT('%', #{keyword}, '%')
                    )
                </if>
                <if test="folderId != null">
                    AND n.folder_id = #{folderId}
                </if>
                <if test="projectId != null">
                    AND n.project_id = #{projectId}
                </if>
                <if test="tagId != null">
                    AND EXISTS (
                        SELECT 1 FROM note_tag nt
                        WHERE nt.workspace_id = n.workspace_id AND nt.note_id = n.id AND nt.tag_id = #{tagId}
                    )
                </if>
            </where>
            ORDER BY n.updated_at DESC, n.id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Note> listSummariesPage(
            @Param("workspaceId") Long workspaceId,
            @Param("keyword") String keyword,
            @Param("folderId") Long folderId,
            @Param("tagId") Long tagId,
            @Param("projectId") Long projectId,
            @Param("offset") long offset,
            @Param("size") int size
    );

    @Select("""
            <script>
            SELECT COUNT(*) FROM notes n
            <where>
                n.workspace_id = #{workspaceId}
                AND n.deleted_at IS NULL
                <if test="keyword != null and keyword != ''">
                    AND (n.title LIKE CONCAT('%', #{keyword}, '%') OR n.content LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test="folderId != null">AND n.folder_id = #{folderId}</if>
                <if test="projectId != null">AND n.project_id = #{projectId}</if>
                <if test="tagId != null">
                    AND EXISTS (
                        SELECT 1 FROM note_tag nt
                        WHERE nt.workspace_id = n.workspace_id AND nt.note_id = n.id AND nt.tag_id = #{tagId}
                    )
                </if>
            </where>
            </script>
            """)
    long count(
            @Param("workspaceId") Long workspaceId,
            @Param("keyword") String keyword,
            @Param("folderId") Long folderId,
            @Param("tagId") Long tagId,
            @Param("projectId") Long projectId
    );

    @Select("""
            SELECT n.id, n.workspace_id, n.title, n.folder_id, f.name AS folder_name, n.version,
                   n.project_id, p.name AS project_name, n.created_at, n.updated_at
            FROM notes n
            LEFT JOIN note_folders f ON f.workspace_id = n.workspace_id AND f.id = n.folder_id
            LEFT JOIN projects p ON p.workspace_id = n.workspace_id AND p.id = n.project_id
            WHERE n.workspace_id = #{workspaceId} AND n.project_id = #{projectId}
              AND n.deleted_at IS NULL
            ORDER BY n.updated_at DESC, n.id DESC
            """)
    List<Note> listSummariesByProjectId(
            @Param("workspaceId") Long workspaceId,
            @Param("projectId") Long projectId
    );

    /**
     * 根据主键查询笔记详情。
     */
    @Select("""
            SELECT n.id, n.workspace_id, n.title, n.content, n.folder_id, f.name AS folder_name, n.version,
                   n.project_id, p.name AS project_name, n.created_at, n.updated_at
            FROM notes n
            LEFT JOIN note_folders f ON f.workspace_id = n.workspace_id AND f.id = n.folder_id
            LEFT JOIN projects p ON p.workspace_id = n.workspace_id AND p.id = n.project_id
            WHERE n.workspace_id = #{workspaceId} AND n.id = #{id} AND n.deleted_at IS NULL
            """)
    Note findDetailById(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    /** 关联和删除前锁定当前笔记，避免同时变更项目造成失效关联。 */
    @Select("""
            SELECT id, workspace_id, project_id, version FROM notes
            WHERE workspace_id = #{workspaceId} AND id = #{id} AND deleted_at IS NULL FOR UPDATE
            """)
    Note lockById(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    /**
     * 插入笔记，并回填自增主键到 note.id。
     */
    @Insert("""
            INSERT INTO notes (workspace_id, title, content, folder_id, project_id, created_at, updated_at)
            VALUES (#{workspaceId}, #{title}, #{content}, #{folderId}, #{projectId}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Note note);

    /**
     * 更新笔记基础字段，不直接处理标签关联。
     */
    @Update("""
            UPDATE notes
            SET title = #{title},
                content = #{content},
                folder_id = #{folderId},
                project_id = #{projectId},
                version = version + 1,
                updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND id = #{id} AND version = #{version}
              AND deleted_at IS NULL
            """)
    int update(Note note);

    @Update("""
            UPDATE notes
            SET deleted_at = #{deletedAt}, deleted_by_subject = #{deletedBySubject},
                version = version + 1, updated_at = #{deletedAt}
            WHERE workspace_id = #{workspaceId} AND id = #{id} AND deleted_at IS NULL
            """)
    int softDelete(Note note);

    @Select("""
            SELECT id, workspace_id, version, deleted_at, deleted_by_subject FROM notes
            WHERE workspace_id = #{workspaceId} AND id = #{id} AND deleted_at IS NOT NULL FOR UPDATE
            """)
    Note lockDeletedById(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    @Update("""
            UPDATE notes
            SET deleted_at = NULL, deleted_by_subject = NULL,
                version = version + 1, updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND id = #{id} AND deleted_at IS NOT NULL
            """)
    int restore(
            @Param("workspaceId") Long workspaceId,
            @Param("id") Long id,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );

    @Delete("DELETE FROM notes WHERE workspace_id = #{workspaceId} AND id = #{id} AND deleted_at IS NOT NULL")
    int permanentlyDelete(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    /** 清理由导入流程在当前事务内创建但尚未完成的笔记。 */
    @Delete("DELETE FROM notes WHERE workspace_id = #{workspaceId} AND id = #{id} AND deleted_at IS NULL")
    int deleteIncompleteImport(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    @Select("""
            <script>
            SELECT n.id, n.workspace_id, n.title, n.folder_id, f.name AS folder_name, n.version,
                   n.project_id, p.name AS project_name, n.deleted_at, n.deleted_by_subject,
                   COALESCE(sp.display_name, sp.username) AS deleted_by_display_name,
                   CASE WHEN #{keyword} IS NULL OR #{keyword} = '' THEN LEFT(n.content, 200)
                        ELSE SUBSTRING(n.content, GREATEST(1, LOCATE(#{keyword}, n.content) - 60), 200)
                   END AS excerpt
            FROM notes n
            LEFT JOIN note_folders f ON f.workspace_id = n.workspace_id AND f.id = n.folder_id
            LEFT JOIN projects p ON p.workspace_id = n.workspace_id AND p.id = n.project_id
            LEFT JOIN security_principals sp ON sp.subject_id = n.deleted_by_subject
            WHERE n.workspace_id = #{workspaceId} AND n.deleted_at IS NOT NULL
            <if test="keyword != null and keyword != ''">
                AND (n.title LIKE CONCAT('%', #{keyword}, '%') OR n.content LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY n.deleted_at DESC, n.id DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<Note> listDeletedPage(
            @Param("workspaceId") Long workspaceId,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("size") int size
    );

    @Select("""
            <script>
            SELECT COUNT(*) FROM notes n
            WHERE n.workspace_id = #{workspaceId} AND n.deleted_at IS NOT NULL
            <if test="keyword != null and keyword != ''">
                AND (n.title LIKE CONCAT('%', #{keyword}, '%') OR n.content LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            </script>
            """)
    long countDeleted(@Param("workspaceId") Long workspaceId, @Param("keyword") String keyword);

    /**
     * 清空某个项目下所有笔记的项目关联。
     *
     * <p>用于删除没有任务的项目时保留笔记。</p>
     */
    @Update("""
            UPDATE notes
            SET project_id = NULL, version = version + 1, updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND project_id = #{projectId}
            """)
    int clearProject(
            @Param("workspaceId") Long workspaceId,
            @Param("projectId") Long projectId,
            @Param("updatedAt") java.time.LocalDateTime updatedAt
    );

    /**
     * 统计某文件夹下的笔记数量，用于文件夹删除前校验。
     */
    @Select("SELECT COUNT(*) FROM notes WHERE workspace_id = #{workspaceId} AND folder_id = #{folderId}")
    long countByFolderId(@Param("workspaceId") Long workspaceId, @Param("folderId") Long folderId);

    /**
     * 按标题统计笔记数量，用于导入时跳过同标题笔记。
     */
    @Select("""
            SELECT COUNT(*) FROM notes
            WHERE workspace_id = #{workspaceId} AND title = #{title} AND deleted_at IS NULL
            """)
    long countByTitle(@Param("workspaceId") Long workspaceId, @Param("title") String title);

    /**
     * 查询最近更新的笔记，用于首页概览。
     */
    @Select("""
            SELECT n.id, n.workspace_id, n.title, n.folder_id, f.name AS folder_name, n.version,
                   n.project_id, p.name AS project_name, n.created_at, n.updated_at
            FROM notes n
            LEFT JOIN note_folders f ON f.workspace_id = n.workspace_id AND f.id = n.folder_id
            LEFT JOIN projects p ON p.workspace_id = n.workspace_id AND p.id = n.project_id
            WHERE n.workspace_id = #{workspaceId} AND n.deleted_at IS NULL
            ORDER BY n.updated_at DESC, n.id DESC
            LIMIT #{limit}
            """)
    List<Note> recentSummaries(@Param("workspaceId") Long workspaceId, @Param("limit") int limit);
}

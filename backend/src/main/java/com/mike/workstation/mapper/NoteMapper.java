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
                   n.project_id, p.name AS project_name, n.created_at, n.updated_at
            FROM notes n
            LEFT JOIN note_folders f ON f.workspace_id = n.workspace_id AND f.id = n.folder_id
            LEFT JOIN projects p ON p.workspace_id = n.workspace_id AND p.id = n.project_id
            <where>
                n.workspace_id = #{workspaceId}
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
            WHERE n.workspace_id = #{workspaceId} AND n.id = #{id}
            """)
    Note findDetailById(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

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
            WHERE workspace_id = #{workspaceId} AND id = #{id}
            """)
    int update(Note note);

    /**
     * 根据主键删除笔记。
     */
    @Delete("DELETE FROM notes WHERE workspace_id = #{workspaceId} AND id = #{id}")
    int delete(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

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
    @Select("SELECT COUNT(*) FROM notes WHERE workspace_id = #{workspaceId} AND title = #{title}")
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
            WHERE n.workspace_id = #{workspaceId}
            ORDER BY n.updated_at DESC, n.id DESC
            LIMIT #{limit}
            """)
    List<Note> recentSummaries(@Param("workspaceId") Long workspaceId, @Param("limit") int limit);
}

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
     * 按标题、正文、分类、标签和项目组合筛选笔记。
     */
    @Select("""
            <script>
            SELECT n.id, n.title, n.content, n.category_id, c.name AS category_name,
                   n.project_id, p.name AS project_name, n.created_at, n.updated_at
            FROM notes n
            LEFT JOIN categories c ON c.id = n.category_id
            LEFT JOIN projects p ON p.id = n.project_id
            <where>
                <if test="keyword != null and keyword != ''">
                    (
                        n.title LIKE CONCAT('%', #{keyword}, '%')
                        OR n.content LIKE CONCAT('%', #{keyword}, '%')
                    )
                </if>
                <if test="categoryId != null">
                    AND n.category_id = #{categoryId}
                </if>
                <if test="projectId != null">
                    AND n.project_id = #{projectId}
                </if>
                <if test="tagId != null">
                    AND EXISTS (
                        SELECT 1 FROM note_tag nt
                        WHERE nt.note_id = n.id AND nt.tag_id = #{tagId}
                    )
                </if>
            </where>
            ORDER BY n.updated_at DESC, n.id DESC
            </script>
            """)
    List<Note> list(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("tagId") Long tagId,
            @Param("projectId") Long projectId
    );

    /**
     * 根据主键查询笔记详情。
     */
    @Select("""
            SELECT n.id, n.title, n.content, n.category_id, c.name AS category_name,
                   n.project_id, p.name AS project_name, n.created_at, n.updated_at
            FROM notes n
            LEFT JOIN categories c ON c.id = n.category_id
            LEFT JOIN projects p ON p.id = n.project_id
            WHERE n.id = #{id}
            """)
    Note findById(Long id);

    /**
     * 插入笔记，并回填自增主键到 note.id。
     */
    @Insert("""
            INSERT INTO notes (title, content, category_id, project_id, created_at, updated_at)
            VALUES (#{title}, #{content}, #{categoryId}, #{projectId}, #{createdAt}, #{updatedAt})
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
                category_id = #{categoryId},
                project_id = #{projectId},
                updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(Note note);

    /**
     * 根据主键删除笔记。
     */
    @Delete("DELETE FROM notes WHERE id = #{id}")
    int delete(Long id);

    /**
     * 清空某个项目下所有笔记的项目关联。
     *
     * <p>用于删除没有任务的项目时保留笔记。</p>
     */
    @Update("UPDATE notes SET project_id = NULL, updated_at = #{updatedAt} WHERE project_id = #{projectId}")
    int clearProject(@Param("projectId") Long projectId, @Param("updatedAt") java.time.LocalDateTime updatedAt);

    /**
     * 统计某分类下的笔记数量，用于分类删除前校验。
     */
    @Select("SELECT COUNT(*) FROM notes WHERE category_id = #{categoryId}")
    long countByCategoryId(Long categoryId);

    /**
     * 按标题统计笔记数量，用于导入时跳过同标题笔记。
     */
    @Select("SELECT COUNT(*) FROM notes WHERE title = #{title}")
    long countByTitle(String title);

    /**
     * 查询最近更新的笔记，用于首页概览。
     */
    @Select("""
            SELECT n.id, n.title, n.content, n.category_id, c.name AS category_name,
                   n.project_id, p.name AS project_name, n.created_at, n.updated_at
            FROM notes n
            LEFT JOIN categories c ON c.id = n.category_id
            LEFT JOIN projects p ON p.id = n.project_id
            ORDER BY n.updated_at DESC, n.id DESC
            LIMIT #{limit}
            """)
    List<Note> recent(int limit);
}

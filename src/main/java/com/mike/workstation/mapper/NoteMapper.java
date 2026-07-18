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
public interface NoteMapper {
    @Select("""
            <script>
            SELECT n.id, n.title, n.content, n.category_id, c.name AS category_name,
                   n.project_id, p.name AS project_name, n.created_at, n.updated_at
            FROM notes n
            LEFT JOIN categories c ON c.id = n.category_id
            LEFT JOIN projects p ON p.id = n.project_id
            <where>
                <if test="keyword != null and keyword != ''">
                    n.title LIKE CONCAT('%', #{keyword}, '%')
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

    @Select("""
            SELECT n.id, n.title, n.content, n.category_id, c.name AS category_name,
                   n.project_id, p.name AS project_name, n.created_at, n.updated_at
            FROM notes n
            LEFT JOIN categories c ON c.id = n.category_id
            LEFT JOIN projects p ON p.id = n.project_id
            WHERE n.id = #{id}
            """)
    Note findById(Long id);

    @Insert("""
            INSERT INTO notes (title, content, category_id, project_id, created_at, updated_at)
            VALUES (#{title}, #{content}, #{categoryId}, #{projectId}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Note note);

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

    @Delete("DELETE FROM notes WHERE id = #{id}")
    int delete(Long id);

    @Update("UPDATE notes SET project_id = NULL, updated_at = #{updatedAt} WHERE project_id = #{projectId}")
    int clearProject(@Param("projectId") Long projectId, @Param("updatedAt") java.time.LocalDateTime updatedAt);

    @Select("SELECT COUNT(*) FROM notes WHERE category_id = #{categoryId}")
    long countByCategoryId(Long categoryId);

    @Select("SELECT COUNT(*) FROM notes WHERE title = #{title}")
    long countByTitle(String title);

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

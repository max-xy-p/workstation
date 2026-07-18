package com.mike.workstation.mapper;

import com.mike.workstation.domain.Tag;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TagMapper {
    @Select("SELECT id, name, created_at, updated_at FROM tags ORDER BY name")
    List<Tag> list();

    @Select("SELECT id, name, created_at, updated_at FROM tags WHERE id = #{id}")
    Tag findById(Long id);

    @Select("""
            <script>
            SELECT id, name, created_at, updated_at
            FROM tags
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            ORDER BY name
            </script>
            """)
    List<Tag> findByIds(@Param("ids") List<Long> ids);

    @Select("""
            SELECT t.id, t.name, t.created_at, t.updated_at
            FROM tags t
            JOIN note_tag nt ON nt.tag_id = t.id
            WHERE nt.note_id = #{noteId}
            ORDER BY t.name
            """)
    List<Tag> findByNoteId(Long noteId);

    @Insert("""
            INSERT INTO tags (name, created_at, updated_at)
            VALUES (#{name}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Tag tag);

    @Update("UPDATE tags SET name = #{name}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(Tag tag);

    @Delete("DELETE FROM tags WHERE id = #{id}")
    int delete(Long id);

    @Insert("INSERT INTO note_tag (note_id, tag_id) VALUES (#{noteId}, #{tagId})")
    int insertNoteTag(@Param("noteId") Long noteId, @Param("tagId") Long tagId);

    @Delete("DELETE FROM note_tag WHERE note_id = #{noteId}")
    int deleteNoteTags(Long noteId);

    @Delete("DELETE FROM note_tag WHERE tag_id = #{tagId}")
    int deleteTagLinks(Long tagId);
}

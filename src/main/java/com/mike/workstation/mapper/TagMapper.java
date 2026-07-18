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
/**
 * 标签表和笔记标签关联表数据访问接口。
 */
public interface TagMapper {
    /**
     * 查询全部标签。
     */
    @Select("SELECT id, name, created_at, updated_at FROM tags ORDER BY name")
    List<Tag> list();

    /**
     * 根据主键查询标签。
     */
    @Select("SELECT id, name, created_at, updated_at FROM tags WHERE id = #{id}")
    Tag findById(Long id);

    /**
     * 根据 ID 集合查询标签，用于校验请求中传入的标签是否都存在。
     */
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

    /**
     * 查询一篇笔记关联的全部标签。
     */
    @Select("""
            SELECT t.id, t.name, t.created_at, t.updated_at
            FROM tags t
            JOIN note_tag nt ON nt.tag_id = t.id
            WHERE nt.note_id = #{noteId}
            ORDER BY t.name
            """)
    List<Tag> findByNoteId(Long noteId);

    /**
     * 插入标签，并回填自增主键到 tag.id。
     */
    @Insert("""
            INSERT INTO tags (name, created_at, updated_at)
            VALUES (#{name}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Tag tag);

    /**
     * 更新标签名称。
     */
    @Update("UPDATE tags SET name = #{name}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(Tag tag);

    /**
     * 根据主键删除标签。
     */
    @Delete("DELETE FROM tags WHERE id = #{id}")
    int delete(Long id);

    /**
     * 新增笔记和标签的关联关系。
     */
    @Insert("INSERT INTO note_tag (note_id, tag_id) VALUES (#{noteId}, #{tagId})")
    int insertNoteTag(@Param("noteId") Long noteId, @Param("tagId") Long tagId);

    /**
     * 删除某篇笔记的全部标签关联。
     */
    @Delete("DELETE FROM note_tag WHERE note_id = #{noteId}")
    int deleteNoteTags(Long noteId);

    /**
     * 删除某个标签的全部笔记关联。
     */
    @Delete("DELETE FROM note_tag WHERE tag_id = #{tagId}")
    int deleteTagLinks(Long tagId);
}

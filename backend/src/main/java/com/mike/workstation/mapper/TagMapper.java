package com.mike.workstation.mapper;

import com.mike.workstation.domain.NoteTagRow;
import com.mike.workstation.domain.Tag;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 标签表和笔记标签关联表数据访问接口。 */
@Mapper
public interface TagMapper {
    /** 查询全部标签。 */
    @Select(
            "SELECT id, workspace_id, name, created_at, updated_at FROM tags WHERE workspace_id = #{workspaceId} ORDER BY name")
    List<Tag> list(Long workspaceId);

    /** 根据主键查询标签。 */
    @Select(
            """
            SELECT id, workspace_id, name, created_at, updated_at
            FROM tags WHERE workspace_id = #{workspaceId} AND id = #{id}
            """)
    Tag findById(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    @Select(
            """
            <script>
            SELECT COUNT(*) FROM tags
            WHERE workspace_id = #{workspaceId} AND name = #{name}
            <if test="excludeId != null">AND id != #{excludeId}</if>
            </script>
            """)
    long countByName(
            @Param("workspaceId") Long workspaceId,
            @Param("name") String name,
            @Param("excludeId") Long excludeId);

    /** 根据 ID 集合查询标签，用于校验请求中传入的标签是否都存在。 */
    @Select(
            """
            <script>
            SELECT id, workspace_id, name, created_at, updated_at
            FROM tags
            WHERE workspace_id = #{workspaceId} AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
            </foreach>
            ORDER BY name
            </script>
            """)
    List<Tag> findByIds(@Param("workspaceId") Long workspaceId, @Param("ids") List<Long> ids);

    /** 查询一篇笔记关联的全部标签。 */
    @Select(
            """
            SELECT t.id, t.name, t.created_at, t.updated_at
            FROM tags t
            JOIN note_tag nt ON nt.workspace_id = t.workspace_id AND nt.tag_id = t.id
            WHERE nt.workspace_id = #{workspaceId} AND nt.note_id = #{noteId}
            ORDER BY t.name
            """)
    List<Tag> findByNoteId(@Param("workspaceId") Long workspaceId, @Param("noteId") Long noteId);

    @Select(
            """
            <script>
            SELECT nt.note_id, t.id, t.name, t.created_at, t.updated_at
            FROM note_tag nt
            JOIN tags t ON t.workspace_id = nt.workspace_id AND t.id = nt.tag_id
            WHERE nt.workspace_id = #{workspaceId} AND nt.note_id IN
            <foreach collection="noteIds" item="noteId" open="(" separator="," close=")">
                #{noteId}
            </foreach>
            ORDER BY nt.note_id, t.name
            </script>
            """)
    List<NoteTagRow> findByNoteIds(
            @Param("workspaceId") Long workspaceId, @Param("noteIds") List<Long> noteIds);

    /** 插入标签，并回填自增主键到 tag.id。 */
    @Insert(
            """
            INSERT INTO tags (workspace_id, name, created_at, updated_at)
            VALUES (#{workspaceId}, #{name}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Tag tag);

    /** 更新标签名称。 */
    @Update(
            """
            UPDATE tags SET name = #{name}, updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND id = #{id}
            """)
    int update(Tag tag);

    /** 根据主键删除标签。 */
    @Delete("DELETE FROM tags WHERE workspace_id = #{workspaceId} AND id = #{id}")
    int delete(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    /** 新增笔记和标签的关联关系。 */
    @Insert(
            """
            INSERT INTO note_tag (workspace_id, note_id, tag_id)
            VALUES (#{workspaceId}, #{noteId}, #{tagId})
            """)
    int insertNoteTag(
            @Param("workspaceId") Long workspaceId,
            @Param("noteId") Long noteId,
            @Param("tagId") Long tagId);

    /** 删除某篇笔记的全部标签关联。 */
    @Delete("DELETE FROM note_tag WHERE workspace_id = #{workspaceId} AND note_id = #{noteId}")
    int deleteNoteTags(@Param("workspaceId") Long workspaceId, @Param("noteId") Long noteId);

    /** 删除某个标签的全部笔记关联。 */
    @Delete("DELETE FROM note_tag WHERE workspace_id = #{workspaceId} AND tag_id = #{tagId}")
    int deleteTagLinks(@Param("workspaceId") Long workspaceId, @Param("tagId") Long tagId);
}

package com.mike.workstation.mapper;

import com.mike.workstation.domain.NoteFolder;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NoteFolderMapper {
    @Select("SELECT id, name, parent_id, created_at, updated_at FROM note_folders ORDER BY name, id")
    List<NoteFolder> list();

    @Select("SELECT id, name, parent_id, created_at, updated_at FROM note_folders WHERE id = #{id}")
    NoteFolder findById(Long id);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM note_folders
            WHERE name = #{name}
              AND
              <choose>
                <when test="parentId == null">parent_id IS NULL</when>
                <otherwise>parent_id = #{parentId}</otherwise>
              </choose>
              <if test="excludeId != null">AND id != #{excludeId}</if>
            </script>
            """)
    long countSiblingName(
            @Param("name") String name,
            @Param("parentId") Long parentId,
            @Param("excludeId") Long excludeId
    );

    @Insert("""
            INSERT INTO note_folders (name, parent_id, created_at, updated_at)
            VALUES (#{name}, #{parentId}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NoteFolder folder);

    @Update("""
            UPDATE note_folders
            SET name = #{name}, parent_id = #{parentId}, updated_at = #{updatedAt}
            WHERE id = #{id}
            """)
    int update(NoteFolder folder);

    @Select("SELECT COUNT(*) FROM note_folders WHERE parent_id = #{parentId}")
    long countChildren(Long parentId);

    @Delete("DELETE FROM note_folders WHERE id = #{id}")
    int delete(Long id);
}

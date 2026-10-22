package com.mike.workstation.mapper;

import com.mike.workstation.domain.NoteFolder;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface NoteFolderMapper {
    @Select(
            """
            SELECT id, workspace_id, name, parent_id, created_at, updated_at
            FROM note_folders WHERE workspace_id = #{workspaceId} ORDER BY name, id
            """)
    List<NoteFolder> list(Long workspaceId);

    @Select(
            """
            SELECT id, workspace_id, name, parent_id, created_at, updated_at
            FROM note_folders WHERE workspace_id = #{workspaceId} AND id = #{id}
            """)
    NoteFolder findById(@Param("workspaceId") Long workspaceId, @Param("id") Long id);

    @Select(
            """
            <script>
            SELECT COUNT(*)
            FROM note_folders
            WHERE workspace_id = #{workspaceId}
              AND name = #{name}
              AND
              <choose>
                <when test="parentId == null">parent_id IS NULL</when>
                <otherwise>parent_id = #{parentId}</otherwise>
              </choose>
              <if test="excludeId != null">AND id != #{excludeId}</if>
            </script>
            """)
    long countSiblingName(
            @Param("workspaceId") Long workspaceId,
            @Param("name") String name,
            @Param("parentId") Long parentId,
            @Param("excludeId") Long excludeId);

    @Insert(
            """
            INSERT INTO note_folders (workspace_id, name, parent_id, created_at, updated_at)
            VALUES (#{workspaceId}, #{name}, #{parentId}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NoteFolder folder);

    @Update(
            """
            UPDATE note_folders
            SET name = #{name}, parent_id = #{parentId}, updated_at = #{updatedAt}
            WHERE workspace_id = #{workspaceId} AND id = #{id}
            """)
    int update(NoteFolder folder);

    @Select(
            """
            SELECT COUNT(*) FROM note_folders
            WHERE workspace_id = #{workspaceId} AND parent_id = #{parentId}
            """)
    long countChildren(@Param("workspaceId") Long workspaceId, @Param("parentId") Long parentId);

    @Delete("DELETE FROM note_folders WHERE workspace_id = #{workspaceId} AND id = #{id}")
    int delete(@Param("workspaceId") Long workspaceId, @Param("id") Long id);
}

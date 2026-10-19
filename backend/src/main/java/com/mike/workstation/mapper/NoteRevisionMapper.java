package com.mike.workstation.mapper;

import com.mike.workstation.domain.NoteRevision;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NoteRevisionMapper {
    String SUMMARY_COLUMNS = "r.id, r.workspace_id, r.note_id, r.note_version, r.title, r.actor_subject, "
            + "COALESCE(p.display_name, p.username) AS actor_display_name, r.created_at, r.source, r.restored_from_revision_id";
    String ACTIVE_JOIN = " FROM note_revisions r JOIN notes n ON n.workspace_id=r.workspace_id AND n.id=r.note_id "
            + "LEFT JOIN security_principals p ON p.subject_id=r.actor_subject "
            + "WHERE r.workspace_id=#{workspaceId} AND r.note_id=#{noteId} AND n.deleted_at IS NULL ";

    @Select("SELECT " + SUMMARY_COLUMNS + ACTIVE_JOIN
            + "AND r.note_version <= #{upToVersion} ORDER BY r.note_version DESC LIMIT #{size} OFFSET #{offset}")
    List<NoteRevision> list(@Param("workspaceId") Long workspaceId, @Param("noteId") Long noteId,
            @Param("upToVersion") long upToVersion, @Param("offset") long offset, @Param("size") int size);

    @Select("SELECT COUNT(*)" + ACTIVE_JOIN + "AND r.note_version <= #{upToVersion}")
    long count(@Param("workspaceId") Long workspaceId, @Param("noteId") Long noteId,
            @Param("upToVersion") long upToVersion);

    @Select("SELECT " + SUMMARY_COLUMNS + ", r.content" + ACTIVE_JOIN + "AND r.id=#{revisionId}")
    NoteRevision find(@Param("workspaceId") Long workspaceId, @Param("noteId") Long noteId,
            @Param("revisionId") Long revisionId);

    /** 调用方已经持有当前笔记的写锁，以当前读取得最新快照，避免旧事务快照影响去重。 */
    @Select("""
            SELECT id, title, content FROM note_revisions
            WHERE workspace_id=#{workspaceId} AND note_id=#{noteId}
            ORDER BY note_version DESC LIMIT 1 FOR UPDATE
            """)
    NoteRevision latestForUpdate(@Param("workspaceId") Long workspaceId, @Param("noteId") Long noteId);

    @Insert("""
            INSERT INTO note_revisions
                (workspace_id, note_id, note_version, title, content, actor_subject, created_at, source, restored_from_revision_id)
            VALUES (#{workspaceId}, #{noteId}, #{noteVersion}, #{title}, #{content}, #{actorSubject},
                    #{createdAt}, #{source}, #{restoredFromRevisionId})
            """)
    @Options(useGeneratedKeys=true, keyProperty="id")
    int insert(NoteRevision revision);
}

package com.mike.workstation.mapper;

import com.mike.workstation.domain.SecurityPrincipal;
import com.mike.workstation.domain.Workspace;
import com.mike.workstation.domain.WorkspaceMember;
import com.mike.workstation.domain.WorkspaceRole;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
/** 安全主体、工作空间和成员关系的数据访问。 */
public interface WorkspaceMapper {
    @Insert("""
            INSERT INTO security_principals
                (subject_id, principal_type, username, display_name, created_at, updated_at)
            VALUES
                (#{subjectId}, #{principalType}, #{username}, #{displayName}, #{createdAt}, #{updatedAt})
            ON DUPLICATE KEY UPDATE
                principal_type = VALUES(principal_type),
                username = VALUES(username),
                display_name = VALUES(display_name),
                updated_at = VALUES(updated_at)
            """)
    int upsertPrincipal(SecurityPrincipal principal);

    @Insert("""
            INSERT INTO workspaces
                (name, type, personal_owner_subject, created_by_subject, created_at, updated_at)
            VALUES
                (#{name}, #{type}, #{personalOwnerSubject}, #{createdBySubject}, #{createdAt}, #{updatedAt})
            ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPersonalWorkspace(Workspace workspace);

    @Insert("""
            INSERT INTO workspaces
                (name, type, personal_owner_subject, created_by_subject, created_at, updated_at)
            VALUES
                (#{name}, #{type}, NULL, #{createdBySubject}, #{createdAt}, #{updatedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertSharedWorkspace(Workspace workspace);

    @Select("""
            SELECT subject_id, principal_type, username, display_name, created_at, updated_at
            FROM security_principals
            WHERE subject_id = #{subjectId}
            """)
    SecurityPrincipal findPrincipal(String subjectId);

    /** 串行化成员变更，使最后一个 Owner 的判断与写入处于同一锁内。 */
    @Select("SELECT id FROM workspaces WHERE id = #{workspaceId} FOR UPDATE")
    Long lockWorkspace(Long workspaceId);

    @Select("""
            SELECT subject_id, principal_type, username, display_name, created_at, updated_at
            FROM security_principals
            WHERE principal_type = 'USER' AND username = #{username}
            ORDER BY subject_id LIMIT 10
            """)
    List<SecurityPrincipal> findUsersByUsername(String username);

    @Insert("""
            INSERT IGNORE INTO workspace_members (workspace_id, principal_subject, role, joined_at)
            VALUES (#{workspaceId}, #{subjectId}, #{role}, #{joinedAt})
            """)
    int insertMember(WorkspaceMember member);

    @Select("""
            SELECT id, name, type, personal_owner_subject, created_by_subject, created_at, updated_at
            FROM workspaces
            WHERE type = 'PERSONAL' AND personal_owner_subject = #{subjectId}
            """)
    Workspace findPersonalByOwner(String subjectId);

    @Select("""
            SELECT w.id, w.name, w.type, w.personal_owner_subject, w.created_by_subject,
                   wm.role, w.created_at, w.updated_at
            FROM workspaces w
            JOIN workspace_members wm ON wm.workspace_id = w.id
            WHERE wm.principal_subject = #{subjectId}
            ORDER BY w.type, w.name, w.id
            """)
    List<Workspace> listForPrincipal(String subjectId);

    @Select("""
            SELECT w.id, w.name, w.type, w.personal_owner_subject, w.created_by_subject,
                   wm.role, w.created_at, w.updated_at
            FROM workspaces w
            JOIN workspace_members wm ON wm.workspace_id = w.id
            WHERE w.id = #{workspaceId} AND wm.principal_subject = #{subjectId}
            """)
    Workspace findForPrincipal(@Param("workspaceId") Long workspaceId, @Param("subjectId") String subjectId);

    @Select("""
            SELECT wm.workspace_id, wm.principal_subject AS subject_id,
                   sp.principal_type, sp.username, sp.display_name, wm.role, wm.joined_at
            FROM workspace_members wm
            JOIN security_principals sp ON sp.subject_id = wm.principal_subject
            WHERE wm.workspace_id = #{workspaceId}
            ORDER BY wm.joined_at, wm.principal_subject
            """)
    List<WorkspaceMember> listMembers(Long workspaceId);

    @Select("""
            SELECT wm.workspace_id, wm.principal_subject AS subject_id,
                   sp.principal_type, sp.username, sp.display_name, wm.role, wm.joined_at
            FROM workspace_members wm
            JOIN security_principals sp ON sp.subject_id = wm.principal_subject
            WHERE wm.workspace_id = #{workspaceId} AND wm.principal_subject = #{subjectId}
            """)
    WorkspaceMember findMember(@Param("workspaceId") Long workspaceId, @Param("subjectId") String subjectId);

    @Update("UPDATE workspaces SET name = #{name}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateWorkspace(Workspace workspace);

    @Update("""
            UPDATE workspace_members SET role = #{role}
            WHERE workspace_id = #{workspaceId} AND principal_subject = #{subjectId}
            """)
    int updateMemberRole(
            @Param("workspaceId") Long workspaceId,
            @Param("subjectId") String subjectId,
            @Param("role") WorkspaceRole role
    );

    @Delete("""
            DELETE FROM workspace_members
            WHERE workspace_id = #{workspaceId} AND principal_subject = #{subjectId}
            """)
    int deleteMember(@Param("workspaceId") Long workspaceId, @Param("subjectId") String subjectId);

    @Select("SELECT COUNT(*) FROM workspace_members WHERE workspace_id = #{workspaceId} AND role = 'OWNER'")
    long countOwners(Long workspaceId);
}

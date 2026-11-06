package com.mike.workstation.mapper;

import com.mike.workstation.domain.WorkspaceMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** 批量重新校验在线主体的当前成员资格及显示资料。 */
@Mapper
public interface NotePresenceMapper {
    @Select("""
            <script>
            SELECT wm.principal_subject AS subject_id, wm.role, sp.username, sp.display_name
            FROM workspace_members wm JOIN security_principals sp ON sp.subject_id=wm.principal_subject
            WHERE wm.workspace_id=#{workspaceId} AND sp.principal_type='USER'
                AND wm.principal_subject IN
                <foreach collection="subjects" item="subject" open="(" close=")" separator=",">
                    #{subject}
                </foreach>
            ORDER BY wm.principal_subject
            </script>
            """)
    List<WorkspaceMember> currentMembers(@Param("workspaceId") Long workspaceId,
            @Param("subjects") List<String> subjects);
}

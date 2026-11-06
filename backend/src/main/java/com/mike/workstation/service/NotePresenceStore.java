package com.mike.workstation.service;

import com.mike.workstation.domain.NotePresenceState;
import com.mike.workstation.exception.NotePresenceException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Redis 只保存在线短租约，不保存正文、草稿或用户提供的显示资料。 */
@Component
public class NotePresenceStore {
    private static final DefaultRedisScript<String> SCRIPT = new DefaultRedisScript<>();

    static {
        SCRIPT.setLocation(new ClassPathResource("redis/note-presence.lua"));
        SCRIPT.setResultType(String.class);
    }
    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public NotePresenceStore(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    private List<String> keys(long workspaceId, long noteId) {
        String key = "workstation:note-presence:{" + workspaceId + ":" + noteId + "}";
        return List.of(key + ":expires", key + ":states");
    }

    public boolean update(long workspaceId, long noteId, String subject, String session,
            int sequence, NotePresenceState state) {
        var result = redis.execute(SCRIPT, keys(workspaceId, noteId), "write",
                session + "|" + subject, Integer.toString(sequence), state.name(), subject);
        if (result == null || result.isEmpty()) {
            throw new NotePresenceException(false);
        }
        if ("-1".equals(result)) {
            throw new NotePresenceException(true);
        }
        return "1".equals(result);
    }

    public Map<String, NotePresenceState> snapshot(long workspaceId, long noteId) {
        String result = redis.execute(SCRIPT, keys(workspaceId, noteId), "read");
        if (result == null) {
            throw new NotePresenceException(false);
        }
        String[] rows = json.readValue(result, String[].class);
        Map<String, NotePresenceState> snapshot = new HashMap<>();
        for (int index = 0; index < rows.length; index += 2) {
            String subject = rows[index].substring(37);
            var state = NotePresenceState.valueOf(rows[index + 1]);
            snapshot.merge(subject, state, (a, b) ->
                    a == NotePresenceState.EDITING || b == NotePresenceState.EDITING ? NotePresenceState.EDITING : NotePresenceState.VIEWING);
        }
        return snapshot;
    }
}

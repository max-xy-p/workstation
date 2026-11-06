-- 同一笔记的两个 Key 使用同一 Hash Tag；所有到期判断使用 Redis 时间。
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now)
for _, member in ipairs(expired) do redis.call('HDEL', KEYS[2], member) end
redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
if ARGV[1] == 'write' then
    local member, sequence, state, subject = ARGV[2], tonumber(ARGV[3]), ARGV[4], ARGV[5]
    local old = redis.call('HGET', KEYS[2], member)
    if old then
        local separator = string.find(old, '|', 1, true)
        if tonumber(string.sub(old, 1, separator - 1)) >= sequence
            or string.sub(old, separator + 1) == 'LEFT' then return '0' end
    else
        if redis.call('ZCARD', KEYS[1]) >= 256 then return '-1' end
        local count = 0
        for _, candidate in ipairs(redis.call('ZRANGE', KEYS[1], 0, -1)) do
            local value = redis.call('HGET', KEYS[2], candidate)
            if string.sub(candidate, 38) == subject and value
                and string.sub(value, -4) ~= 'LEFT' then count = count + 1 end
        end
        if count >= 10 and state ~= 'LEFT' then return '-1' end
    end
    redis.call('HSET', KEYS[2], member, tostring(sequence) .. '|' .. state)
    redis.call('ZADD', KEYS[1], now + (state == 'LEFT' and 120000 or 45000), member)
    redis.call('PEXPIRE', KEYS[1], 150000)
    redis.call('PEXPIRE', KEYS[2], 150000)
    return '1'
end
local result = {}
for _, member in ipairs(redis.call('ZRANGE', KEYS[1], 0, -1)) do
    local value = redis.call('HGET', KEYS[2], member)
    if value and string.sub(value, -4) ~= 'LEFT' then
        table.insert(result, member)
        table.insert(result, string.sub(value, string.find(value, '|', 1, true) + 1))
    end
end
if #result == 0 then return '[]' end
return cjson.encode(result)

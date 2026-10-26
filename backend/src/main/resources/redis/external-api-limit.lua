-- 单 Key 包含令牌桶与冷却截止时间；使用 Redis 时钟避免执行器时钟偏差。
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local cooldown = tonumber(ARGV[3])
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local values = redis.call('HMGET', key, 'tokens', 'updated', 'blocked')
local tokens = math.min(capacity, tonumber(values[1]) or capacity)
local updated = tonumber(values[2]) or now
local blocked = tonumber(values[3]) or 0
local wait = 0
if cooldown > 0 then
    -- 迟到的短冷却不得缩短已经发布的长冷却。
    blocked = math.max(blocked, now + cooldown)
else
    tokens = math.min(capacity, tokens + math.max(0, now - updated) * rate / 1000)
    if blocked > now then
        wait = blocked - now
    elseif tokens >= 1 then
        tokens = tokens - 1
    else
        wait = math.ceil((1 - tokens) * 1000 / rate)
    end
end
redis.call('HSET', key, 'tokens', tokens, 'updated', now, 'blocked', blocked)
local ttl = math.ceil(math.max(capacity * 1000 / rate, blocked - now, 1000)) + 1000
redis.call('PEXPIRE', key, ttl)
if cooldown > 0 then
    return math.ceil(blocked - now)
end
return math.ceil(wait)

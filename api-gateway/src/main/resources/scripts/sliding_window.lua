-- KEYS: [key1, key2, ...]
-- ARGV: [window_ms, current_timestamp, limit1, limit2, ...]
-- Returns: 0 if allowed, 1 if blocked

local window = tonumber(table.remove(ARGV, 1))
local now = tonumber(table.remove(ARGV, 1))
local n_keys = #KEYS

-- Check if any limit is exceeded without modifying yet (or modify as we go?)
-- Verification logic:
-- For each key:
--   remove old
--   count
--   if count >= limit -> return 1 (blocked)
-- If all ok -> add current_timestamp to all keys

-- First pass: Check counts
for i = 1, n_keys do
    local key = KEYS[i]
    local limit = tonumber(ARGV[i])
    
    -- Cleanup old
    redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
    
    -- Count
    local count = redis.call('ZCARD', key)
    
    if count >= limit then
        return 1 -- Blocked
    end
end

-- Second pass: Add current request (only if no limit exceeded)
for i = 1, n_keys do
    local key = KEYS[i]
    redis.call('ZADD', key, now, now)
    redis.call('PEXPIRE', key, window) -- Auto-cleanup key if idle
end

return 0 -- Allowed

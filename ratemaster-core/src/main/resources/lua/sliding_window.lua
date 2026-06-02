-- args
local capacity = tonumber(ARGV[1])
local window_sec = tonumber(ARGV[2])
local unique_id = ARGV[3]

local key = KEYS[1]

-- redis.call('TIME') returns {seconds, microseconds}
local time = redis.call('TIME')
local now_us = tonumber(time[1]) * 1000000 + tonumber(time[2])
local window_us = window_sec * 1000000

local window_start_us = now_us - window_us

-- 1. Remove events outside the current window
redis.call('ZREMRANGEBYSCORE', key, 0, window_start_us)

-- 2. Count events in the current window
local count = redis.call('ZCARD', key)

if count < capacity then
    -- Allowed
    local member = tostring(now_us) .. '-' .. unique_id
    redis.call('ZADD', key, now_us, member)
    redis.call('PEXPIRE', key, window_sec * 1000)
    
    local remaining = capacity - count - 1
    return {1, remaining, 0}
else
    -- Rejected
    -- Find the oldest event in the window to calculate retry_after
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    local retry_after_ms = 0
    if oldest and #oldest >= 2 then
        local oldest_us = tonumber(oldest[2])
        local retry_us = oldest_us + window_us - now_us
        retry_after_ms = math.ceil(retry_us / 1000)
    end
    
    return {0, 0, retry_after_ms}
end

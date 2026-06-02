-- args
local capacity = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2]) * 1000

local key = KEYS[1]

-- redis.call('TIME') returns {seconds, microseconds}
local time = redis.call('TIME')
local now_ms = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

-- Calculate absolute window end time
local window_end_ms = (math.floor(now_ms / window_ms) + 1) * window_ms

-- Increment the counter
local count = redis.call('INCR', key)

-- Set expiration only on the first request
if count == 1 then
    -- PEXPIREAT sets the exact expiration timestamp in milliseconds
    redis.call('PEXPIREAT', key, window_end_ms)
end

if count <= capacity then
    -- Allowed
    local remaining = capacity - count
    return {1, remaining, 0}
else
    -- Rejected
    -- Calculate retry after based on the exact window end
    local retry_after_ms = window_end_ms - now_ms
    return {0, 0, retry_after_ms}
end

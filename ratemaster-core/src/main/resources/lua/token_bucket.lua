--[[
    Token Bucket Rate Limiter - Atomic Lua Script (ADR-002)

    Implements a lazy-refill Token Bucket algorithm with atomic state management.
    Uses redis.call('TIME') for server-side timestamps to eliminate clock-skew
    across distributed application instances.

    KEYS[1] = bucket key (e.g., "ratemaster:tokenbucket:api:/login:192.168.1.1")
    ARGV[1] = max capacity (long)
    ARGV[2] = refill rate in tokens per second (double)
    ARGV[3] = requested tokens / cost (long, typically 1)

    Returns: {allowed (0|1), remainingTokens (long), retryAfterMillis (long)}

    @since 0.1.0
--]]

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_rate = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])

-- Server-side timestamp: eliminates clock-skew across app instances
-- TIME returns {seconds, microseconds} as strings
local time = redis.call('TIME')
local now = tonumber(time[1]) + tonumber(time[2]) / 1000000

-- Load existing bucket state or initialize
local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1])
local last_refill = tonumber(bucket[2])

if tokens == nil then
    -- First request: bucket starts full
    tokens = capacity
    last_refill = now
end

-- Lazy refill: calculate tokens accumulated since last access
local elapsed = math.max(0, now - last_refill)
tokens = math.min(capacity, tokens + elapsed * refill_rate)

-- Evaluate request
local allowed
local remaining
local retry_after_ms

if tokens >= cost then
    -- Consume token(s)
    tokens = tokens - cost
    allowed = 1
    remaining = math.floor(tokens)
    retry_after_ms = 0
else
    -- Insufficient tokens: calculate wait time
    allowed = 0
    remaining = math.floor(tokens)
    local deficit = cost - tokens
    retry_after_ms = math.ceil(deficit / refill_rate * 1000)
end

-- Persist updated state
redis.call('HMSET', key, 'tokens', tostring(tokens), 'last_refill', tostring(now))

-- Set TTL to auto-cleanup inactive buckets (full refill time + buffer)
local ttl = math.ceil(capacity / refill_rate) + 10
redis.call('EXPIRE', key, ttl)

return {allowed, remaining, retry_after_ms}

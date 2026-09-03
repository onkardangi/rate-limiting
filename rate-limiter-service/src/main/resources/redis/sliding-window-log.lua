local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local ttl_seconds = tonumber(ARGV[3])
local member = ARGV[4]

local time = redis.call('TIME')
local now_ms = (time[1] * 1000) + math.floor(time[2] / 1000)
local window_start = now_ms - window_ms

redis.call('ZREMRANGEBYSCORE', key, '-inf', window_start)

local count = redis.call('ZCARD', key)

if count >= limit then
  return 0
end

redis.call('ZADD', key, now_ms, member)
redis.call('EXPIRE', key, ttl_seconds)

return 1

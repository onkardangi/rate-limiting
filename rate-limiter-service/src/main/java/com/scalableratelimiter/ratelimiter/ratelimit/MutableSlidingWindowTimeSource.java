package com.scalableratelimiter.ratelimiter.ratelimit;

import java.time.Duration;

public final class MutableSlidingWindowTimeSource implements SlidingWindowTimeSource {

    private long nowEpochMillis;

    public MutableSlidingWindowTimeSource(long nowEpochMillis) {
        this.nowEpochMillis = nowEpochMillis;
    }

    @Override
    public long nowEpochMillis() {
        return nowEpochMillis;
    }

    public void setNowEpochMillis(long nowEpochMillis) {
        this.nowEpochMillis = nowEpochMillis;
    }

    public void advance(Duration duration) {
        nowEpochMillis += duration.toMillis();
    }
}
